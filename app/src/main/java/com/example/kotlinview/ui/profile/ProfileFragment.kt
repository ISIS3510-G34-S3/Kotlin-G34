package com.example.kotlinview.ui.profile

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import coil.load
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.databinding.FragmentProfileBinding
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

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) vm.onPhotoPicked(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { vm.refresh() }

        binding.btnEdit.setOnClickListener { vm.startEdit() }
        binding.btnCancel.setOnClickListener {
            hideKeyboard()
            vm.cancelEdit()
        }

        binding.btnSave.setOnClickListener {
            hideKeyboard()
            vm.save()
        }

        binding.avatar.setOnClickListener {
            val st = vm.state.value
            if (st.isEditing) pickPhoto.launch("image/*")
        }

        binding.etName.addTextChangedListenerSimple { vm.setDraftName(it) }
        binding.etAbout.addTextChangedListenerSimple { vm.setDraftAbout(it) }
        binding.etLanguages.addTextChangedListenerSimple { vm.setDraftLanguages(it) }

        viewLifecycleOwner.lifecycleScope.launchWhenStarted {
            vm.state.collectLatest { st ->
                render(st)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refresh() // refresh whenever profile is viewed (best-effort)
    }

    private fun render(st: ProfileUiState) {
        val isInitialLoading = st.isLoading && st.profile == null

        // 1) Top-level containers (prevents flicker)
        binding.loadingOverlay.visibility = if (isInitialLoading) View.VISIBLE else View.GONE
        binding.emptyState.visibility = if (!isInitialLoading && st.showEmptyState) View.VISIBLE else View.GONE
        binding.scroll.visibility = if (!isInitialLoading && !st.showEmptyState) View.VISIBLE else View.GONE

        // 2) Pending sync banner
        binding.cardPending.visibility = if (st.pendingSync) View.VISIBLE else View.GONE

        // 3) Toolbar buttons
        binding.btnEdit.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.btnSave.visibility = if (st.isEditing) View.VISIBLE else View.GONE
        binding.btnCancel.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        // 4) Switch between view-mode TextViews and edit-mode TextInputs
        binding.tvName.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.tilName.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        binding.tvAbout.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.tilAbout.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        binding.tvLanguages.visibility = if (!st.isEditing) View.VISIBLE else View.GONE
        binding.tilLanguages.visibility = if (st.isEditing) View.VISIBLE else View.GONE

        // If no profile yet, stop here (but AFTER toggling visibility so UI is consistent)
        val p = st.profile ?: return

        // 5) Display values (view mode)
        binding.tvName.text = p.name
        binding.tvEmail.text = p.email
        binding.tvAbout.text = p.about.ifBlank { "—" }
        binding.tvLanguages.text = if (p.languages.isEmpty()) "—" else p.languages.joinToString(", ")
        binding.tvRating.text = "Rating: ${String.format(Locale.US, "%.2f", p.avgHostRating)}"
        binding.tvCreatedAt.text = formatMemberSince(p.createdAtMs)

        // 6) Draft values (edit mode)
        if (st.isEditing) {
            // Only setText if different, to avoid cursor jumps / broken typing
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

            val curLang = binding.etLanguages.text?.toString().orEmpty()
            if (curLang != st.draftLanguages) {
                binding.etLanguages.setText(st.draftLanguages)
                binding.etLanguages.setSelection(binding.etLanguages.text?.length ?: 0)
            }
        }

        // 7) Photo: pending -> cached -> placeholder
        val pendingPath = p.pendingPhotoPath
        val cachedPath = p.photoCachePath

        when {
            !pendingPath.isNullOrBlank() -> binding.avatar.load(File(pendingPath)) { crossfade(true) }
            cachedPath.isNotBlank() && File(cachedPath).exists() -> binding.avatar.load(File(cachedPath)) { crossfade(true) }
            else -> binding.avatar.setImageResource(com.example.kotlinview.R.drawable.ic_avatar_placeholder)
        }
    }

    private fun formatMemberSince(createdAtMs: Long): String {
        if (createdAtMs <= 0L) return "Member since —"
        val sdf = SimpleDateFormat("MMMM yyyy", Locale.US)
        return "Member since ${sdf.format(Date(createdAtMs))}"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun Fragment.hideKeyboard() {
        val view = activity?.currentFocus ?: view ?: return
        val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                as android.view.inputmethod.InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }

}

/** Minimal text change helper to avoid boilerplate. */
private fun android.widget.EditText.addTextChangedListenerSimple(onChanged: (String) -> Unit) {
    this.addTextChangedListener(object : android.text.TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
            onChanged(s?.toString().orEmpty())
        }
        override fun afterTextChanged(s: android.text.Editable?) {}
    })

}
