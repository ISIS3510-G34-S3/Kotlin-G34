package com.example.kotlinview.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.core.content.ContextCompat
import androidx.core.util.Pair
import com.example.kotlinview.R
import com.example.kotlinview.databinding.BottomSheetFiltersBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.DateFormat
import java.util.Date

class FilterBottomSheet : BottomSheetDialogFragment() {

    var onApply: ((FilterOptions) -> Unit)? = null
    var onClear: (() -> Unit)? = null

    private var _binding: BottomSheetFiltersBinding? = null
    private val binding get() = _binding!!

    // Chips “demo”
    private val activityTypes = listOf("Cooking", "Photography", "Sports", "Art", "Music", "Language")
    private val durations = listOf("1-2 hours", "3-4 hours", "5+ hours", "Full day")

    // Estado de fechas (millis UTC)
    private var selectedStartAtMs: Long? = null
    private var selectedEndAtMs: Long? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomSheetFiltersBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Referencias a los EditText de fecha
        val etDateStart: EditText = view.findViewById(R.id.et_date_start)
        val etDateEnd: EditText   = view.findViewById(R.id.et_date_end)

        // Cerrar
        binding.btnClose.setOnClickListener { dismiss() }

        // Estilos para chips
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
            chip.setOnClickListener { chip.isChecked = !chip.isChecked }
        }

        // Chips Activity (multi)
        activityTypes.forEach { label ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = label
            stylize(chip)
            binding.chipsActivity.addView(chip)
        }
        binding.chipsActivity.isSingleSelection = false
        binding.chipsActivity.isSelectionRequired = false

        // Chips Duration (single)
        durations.forEach { label ->
            val chip = Chip(requireContext(), null, com.google.android.material.R.style.Widget_Material3_Chip_Filter)
            chip.text = label
            stylize(chip)
            binding.chipsDuration.addView(chip)
        }
        binding.chipsDuration.isSingleSelection = true
        binding.chipsDuration.isSelectionRequired = false

        // --------- Date Range Picker ----------
        val dateFormatter = DateFormat.getDateInstance(DateFormat.MEDIUM)

        fun openRangePicker() {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.filters_pick_dates))

            // Restaurar selección previa si existe
            val preStart = selectedStartAtMs
            val preEnd = selectedEndAtMs
            if (preStart != null && preEnd != null) {
                builder.setSelection(Pair(preStart, preEnd))
            }

            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { range ->
                selectedStartAtMs = range.first
                selectedEndAtMs = range.second
                etDateStart.setText(range.first?.let { dateFormatter.format(Date(it)) } ?: "")
                etDateEnd.setText(range.second?.let { dateFormatter.format(Date(it)) } ?: "")
            }
            picker.show(parentFragmentManager, "date_range_picker")
        }

        // Abrir picker tocando cualquiera de los dos campos
        etDateStart.setOnClickListener { openRangePicker() }
        etDateEnd.setOnClickListener   { openRangePicker() }
        // --------------------------------------

        // Clear
        binding.btnClear.setOnClickListener {
            binding.etLocation.setText("")
            binding.etSkills.setText("")
            binding.chipAccessibility.isChecked = false
            binding.chipsActivity.clearCheck()
            binding.chipsDuration.clearCheck()
            etDateStart.setText("")
            etDateEnd.setText("")
            selectedStartAtMs = null
            selectedEndAtMs = null
            onClear?.invoke()
        }

        binding.btnApply.setOnClickListener {
            val selectedActivities = binding.chipsActivity.checkedChipIds
                .mapNotNull { id -> binding.chipsActivity.findViewById<Chip>(id)?.text?.toString() }

            val selectedDuration: String? = binding.chipsDuration.checkedChipId.let { id ->
                if (id != View.NO_ID) binding.chipsDuration.findViewById<Chip>(id).text.toString() else null
            }

            val locationNorm: String? =
                binding.etLocation.text?.toString()?.trim().let { s ->
                    if (s.isNullOrEmpty()) null else s
                }

            val skillsNorm: String? =
                binding.etSkills.text?.toString()?.trim().let { s ->
                    if (s.isNullOrEmpty()) null else s
                }

            val result = FilterOptions(
                activityTypes = selectedActivities,
                duration = selectedDuration,
                location = locationNorm,
                skillsQuery = skillsNorm,
                accessibility = binding.chipAccessibility.isChecked,
                startAtMs = selectedStartAtMs,
                endAtMs = selectedEndAtMs
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

