package com.example.kotlinview.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.example.kotlinview.R
import com.example.kotlinview.databinding.BottomSheetFiltersBinding
import androidx.core.content.ContextCompat

class FilterBottomSheet : BottomSheetDialogFragment() {

    var onApply: ((FilterOptions) -> Unit)? = null
    var onClear: (() -> Unit)? = null

    private val activityTypes = listOf("Cooking", "Photography", "Sports", "Art", "Music", "Language")
    private val durations = listOf("1-2 hours", "3-4 hours", "5+ hours", "Full day")

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Campos de fecha por id (evita unresolved)
        val etDateStart: EditText = view.findViewById(R.id.et_date_start)
        val etDateEnd: EditText   = view.findViewById(R.id.et_date_end)

        // Cerrar
        binding.btnClose.setOnClickListener { dismiss() }

        // Estilos para chips (color por estado)
        val bg = ContextCompat.getColorStateList(requireContext(), R.color.chip_bg_selector)
        val stroke = ContextCompat.getColorStateList(requireContext(), R.color.chip_stroke_selector)
        val text = ContextCompat.getColorStateList(requireContext(), R.color.chip_text_selector)
        val strokeWidth = resources.getDimension(R.dimen.chip_stroke_width)

        fun stylize(chip: Chip) {
            chip.isCheckable = true
            chip.isClickable = true
            chip.isCheckedIconVisible = false
            chip.setEnsureMinTouchTargetSize(false)

            chip.chipBackgroundColor = bg
            chip.chipStrokeColor = stroke
            chip.chipStrokeWidth = strokeWidth
            chip.setTextColor(text)

            // 🔒 fuerza el toggle incluso si algún estilo lo bloquea
            chip.setOnClickListener { chip.isChecked = !chip.isChecked }
        }

        // Activity (multi)
        activityTypes.forEach { label ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = label
            stylize(chip)
            binding.chipsActivity.addView(chip)
        }
        // Multi selección (default), no requerimos al menos uno
        binding.chipsActivity.isSingleSelection = false
        binding.chipsActivity.isSelectionRequired = false

        // Duration (single)
        durations.forEach { label ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = label
            stylize(chip)
            binding.chipsDuration.addView(chip)
        }
        binding.chipsDuration.isSingleSelection = true
        binding.chipsDuration.isSelectionRequired = false

        // Clear
        binding.btnClear.setOnClickListener {
            binding.etLocation.setText("")
            binding.etSkills.setText("")
            binding.chipAccessibility.isChecked = false
            etDateStart.setText("")
            etDateEnd.setText("")
            binding.chipsActivity.clearCheck()
            binding.chipsDuration.clearCheck()
            onClear?.invoke()
        }

        // Apply
        binding.btnApply.setOnClickListener {
            val selectedActivities = binding.chipsActivity.checkedChipIds
                .mapNotNull { binding.chipsActivity.findViewById<Chip>(it)?.text?.toString() }

            val selectedDuration = binding.chipsDuration.checkedChipId.let { id ->
                if (id != View.NO_ID) binding.chipsDuration.findViewById<Chip>(id).text.toString() else null
            }

            val result = FilterOptions(
                location = binding.etLocation.text?.toString().orEmpty(),
                activityTypes = selectedActivities,
                duration = selectedDuration,
                skillsQuery = binding.etSkills.text?.toString().orEmpty(),
                accessibility = binding.chipAccessibility.isChecked,
                dateStart = etDateStart.text?.toString(),
                dateEnd = etDateEnd.text?.toString()
            )
            onApply?.invoke(result)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        fun newInstance(): FilterBottomSheet = FilterBottomSheet()
    }
}
