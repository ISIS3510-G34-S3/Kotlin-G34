package com.example.kotlinview.ui.profile

import android.content.res.ColorStateList
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.kotlinview.R
import com.example.kotlinview.core.NetworkUtil
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.databinding.FragmentProfileBinding
import com.google.android.material.chip.Chip
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private val vm: ProfileViewModel by viewModels {
        ProfileVmFactory(ServiceLocator.provideProfileRepository(requireContext().applicationContext))
    }

    // For chip-based edit UX (we keep VM draftLanguages as String for compatibility)
    private val editingLanguages = LinkedHashSet<String>()
    private var lastIsEditing: Boolean = false

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            val wasOnline = NetworkUtil.isOnline(requireContext())
            vm.onPhotoPicked(uri)
            if (!wasOnline) {
                showInfoDialog(
                    title = "Saved offline",
                    message = "Your changes are saved on this device and will sync automatically when you’re back online."
                )
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tintRatingStarsYellow()

        binding.btnRetry.setOnClickListener { vm.refresh() }
        binding.btnEdit.setOnClickListener { vm.startEdit() }

        // Bottom buttons (now at end of view)
        binding.btnCancel.setOnClickListener {
            hideKeyboard()
            vm.cancelEdit()
        }

        binding.btnSave.setOnClickListener {
            hideKeyboard()
            val wasOnline = NetworkUtil.isOnline(requireContext())
            vm.save()
            if (!wasOnline) {
                showInfoDialog(
                    title = "Saved offline",
                    message = "Your changes are saved on this device and will sync automatically when you’re back online."
                )
            }
        }

        // Add language UX (chip-based)
        binding.btnAddLanguage.setOnClickListener { addLanguageFromInput() }
        binding.etLanguageAdd.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addLanguageFromInput()
                true
            } else {
                false
            }
        }

        binding.avatar.setOnClickListener {
            val st = vm.state.value
            if (st.isEditing) pickPhoto.launch("image/*")
        }

        binding.chipSyncStatus.setOnClickListener {
            val st = vm.state.value
            if (st.pendingSync) {
                showInfoDialog(
                    title = "Pending sync",
                    message = "Your profile is saved on this device and will sync automatically when you’re online."
                )
            } else {
                showInfoDialog(
                    title = "Synced",
                    message = "Your profile is up to date."
                )
            }
        }

        // Draft fields
        binding.etName.addTextChangedListenerSimple { vm.setDraftName(it) }
        binding.etAbout.addTextChangedListenerSimple { vm.setDraftAbout(it) }
        // NOTE: languages are edited via chips now, not a comma-separated text field

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { st -> render(st) }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }

    private fun render(st: ProfileUiState) {
        val isInitialLoading = st.isLoading && st.profile == null

        binding.loadingOverlay.visibility = if (isInitialLoading) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (!isInitialLoading && st.showEmptyState) View.VISIBLE else View.GONE
        binding.scroll.visibility = if (!isInitialLoading && !st.showEmptyState) View.VISIBLE else View.GONE

        // Toolbar button
        binding.btnEdit.visibility = if (!st.isEditing) View.VISIBLE else View.GONE

        // Bottom actions
        binding.bottomActions.visibility = if (st.isEditing) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (st.isEditing) View.VISIBLE else View.GONE
        binding.btnCancel.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        // View vs edit fields
        binding.tvName.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.tilName.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        binding.tvAbout.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.tilAbout.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        // Languages: chips always visible; add-row only in edit
        binding.langAddRow.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        // Sync status pill
        applySyncStatusChip(isPending = st.pendingSync)

        val p = st.profile ?: return

        // Header
        binding.tvName.text = p.name
        binding.tvEmail.text = p.email
        binding.tvCreatedAt.text = formatJoined(p.createdAtMs)

        // About title like the screenshot ("About <name>")
        binding.tvAboutTitle.text = "About ${p.name}"
        binding.tvAbout.text = p.about.ifBlank { "—" }

        // Rating stars + numeric value
        binding.ratingBar.rating = p.avgHostRating.toFloat()
        binding.tvRatingValue.text = String.format(Locale.US, "%.1f", p.avgHostRating)

        // Draft values (edit mode) — guarded to avoid cursor jumps
        if (st.isEditing) {
            val curName = binding.etName.text?.toString().orEmpty()
            if (curName != st.draftName) {
                binding.etName.setText(st.draftName)
                binding.etName.setSelection(binding.etName.text?.length ?: 0)
            }

            val curAbout = binding.etAbout.text?.toString().orEmpty()
            if (curAbout != st.draftAbout) {
                binding.etAbout.setText(st.draftAbout)
                binding.etAbout.setSelection(binding.etAbout.text?.length ?: 0)
            }
        }

        // Languages chips (view vs edit)
        if (st.isEditing) {
            // Initialize editable set once per edit session
            if (!lastIsEditing) {
                editingLanguages.clear()
                editingLanguages.addAll(
                    parseLanguagesFromDraftOrProfile(
                        draft = st.draftLanguages,
                        fallback = p.languages
                    )
                )
                // Keep VM in sync with normalized list at start of edit
                vm.setDraftLanguages(editingLanguages.joinToString(", "))
                binding.etLanguageAdd.setText("")
                binding.tilLanguageAdd.error = null
            }
            renderLanguageChips(editingLanguages.toList(), editable = true)
        } else {
            if (lastIsEditing) {
                editingLanguages.clear()
                binding.etLanguageAdd.setText("")
                binding.tilLanguageAdd.error = null
            }
            renderLanguageChips(p.languages, editable = false)
        }
        lastIsEditing = st.isEditing

        // Photo: pending -> cached -> placeholder
        val pendingPath = p.pendingPhotoPath
        val cachedPath = p.photoCachePath

        when {
            !pendingPath.isNullOrBlank() -> binding.avatar.load(File(pendingPath)) { crossfade(true) }
            cachedPath.isNotBlank() && File(cachedPath).exists() -> binding.avatar.load(File(cachedPath)) { crossfade(true) }
            else -> binding.avatar.setImageResource(R.drawable.ic_avatar_placeholder)
        }
    }

    private fun addLanguageFromInput() {
        val raw = binding.etLanguageAdd.text?.toString().orEmpty().trim()
        if (raw.isBlank()) {
            binding.tilLanguageAdd.error = "Enter a language"
            return
        }
        binding.tilLanguageAdd.error = null

        val normalized = normalizeLanguage(raw)
        editingLanguages.add(normalized)

        binding.etLanguageAdd.setText("")
        renderLanguageChips(editingLanguages.toList(), editable = true)

        // Keep VM draft string aligned (compat with current VM API)
        vm.setDraftLanguages(editingLanguages.joinToString(", "))
    }

    private fun normalizeLanguage(s: String): String {
        val trimmed = s.trim()
        if (trimmed.isEmpty()) return trimmed
        // Simple normalization: capitalize first char
        return trimmed.replaceFirstChar { ch ->
            if (ch.isLowerCase()) ch.titlecase(Locale.getDefault()) else ch.toString()
        }
    }

    private fun parseLanguagesFromDraftOrProfile(draft: String, fallback: List<String>): List<String> {
        val fromDraft = draft
            .split(",", ";", "\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val source = if (fromDraft.isNotEmpty()) fromDraft else fallback

        return source
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { normalizeLanguage(it) }
            .distinct()
    }

    private fun renderLanguageChips(languages: List<String>, editable: Boolean) {
        binding.chipGroupLanguages.removeAllViews()

        val normalized = languages
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        if (normalized.isEmpty()) {
            binding.chipGroupLanguages.addView(makeLanguageChip("—", editable = false, onRemove = null))
            return
        }

        normalized.forEach { lang ->
            val chip = makeLanguageChip(
                text = lang,
                editable = editable,
                onRemove = if (editable) {
                    {
                        editingLanguages.remove(lang)
                        renderLanguageChips(editingLanguages.toList(), editable = true)
                        vm.setDraftLanguages(editingLanguages.joinToString(", "))
                    }
                } else null
            )
            binding.chipGroupLanguages.addView(chip)
        }
    }

    private fun makeLanguageChip(
        text: String,
        editable: Boolean,
        onRemove: (() -> Unit)?
    ): Chip {
        val chip = Chip(requireContext())
        chip.text = text
        chip.isClickable = false
        chip.isCheckable = false

        val bg = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant)
        val outline = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOutline)
        val onSurface = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorOnSurface)

        chip.chipBackgroundColor = ColorStateList.valueOf(bg)
        chip.chipStrokeColor = ColorStateList.valueOf(outline)
        chip.chipStrokeWidth = 1f
        chip.setTextColor(onSurface)

        if (editable && onRemove != null && text != "—") {
            chip.isCloseIconVisible = true
            chip.setOnCloseIconClickListener { onRemove() }
        } else {
            chip.isCloseIconVisible = false
        }

        return chip
    }

    private fun applySyncStatusChip(isPending: Boolean) {
        val chip = binding.chipSyncStatus

        val neutralBg = MaterialColors.getColor(chip, com.google.android.material.R.attr.colorSurfaceVariant)
        val okColor = ContextCompat.getColor(requireContext(), R.color.british_racing_green)
        val pendingColor = ContextCompat.getColor(requireContext(), R.color.rufous)

        chip.chipBackgroundColor = ColorStateList.valueOf(neutralBg)
        chip.chipStrokeWidth = 1f

        if (isPending) {
            chip.text = "Pending sync"
            chip.setChipIconResource(R.drawable.ic_clock)

            chip.setTextColor(pendingColor)
            chip.chipIconTint = ColorStateList.valueOf(pendingColor)
            chip.chipStrokeColor = ColorStateList.valueOf(pendingColor)
        } else {
            chip.text = "Synced"
            chip.setChipIconResource(R.drawable.ic_check_circle)

            chip.setTextColor(okColor)
            chip.chipIconTint = ColorStateList.valueOf(okColor)
            chip.chipStrokeColor = ColorStateList.valueOf(okColor)
        }
    }

    private fun tintRatingStarsYellow() {
        val yellow = ContextCompat.getColor(requireContext(), R.color.yellow_500)
        val list = ColorStateList.valueOf(yellow)

        binding.ratingBar.progressTintList = list
        binding.ratingBar.secondaryProgressTintList = list
        binding.ratingBar.indeterminateTintList = list
    }

    private fun showInfoDialog(title: String, message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun formatJoined(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Joined in —"
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        return "Joined in ${sdf.format(Date(createdAtMs))}"
    }

    private fun hideKeyboard() {
        try {
            val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(view?.windowToken, 0)
        } catch (_: Throwable) {}
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

/** Minimal text change helper to avoid boilerplate. */
private fun EditText.addTextChangedListenerSimple(onChanged: (String) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })
}
