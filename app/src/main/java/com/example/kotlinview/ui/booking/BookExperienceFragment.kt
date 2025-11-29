package com.example.kotlinview.ui.booking

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import coil.load
import com.example.kotlinview.R
import com.example.kotlinview.databinding.FragmentBookExperienceBinding
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

class BookExperienceFragment : Fragment() {

    private var _binding: FragmentBookExperienceBinding? = null
    private val binding get() = _binding!!

    private var experienceId: String? = null
    private var title: String? = null
    private var hostName: String? = null
    private var department: String? = null
    private var duration: Int = 0
    private var pricePerPerson: Long = 0L
    private var imageUrl: String? = null

    private var startDateMs: Long? = null
    private var endDateMs: Long? = null
    private var guestCount: Int = 1

    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val currencyFormatter: NumberFormat =
        NumberFormat.getCurrencyInstance(Locale("es", "CO"))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        arguments?.let { args ->
            experienceId   = args.getString("experienceId")
            title          = args.getString("experienceTitle")
            hostName       = args.getString("hostName")
            department     = args.getString("department")
            duration       = args.getInt("duration")
            pricePerPerson = args.getLong("pricePerPerson")
            imageUrl       = args.getString("imageUrl")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBookExperienceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupHeader()
        bindExperienceSummary()
        setupDateSelection()
        setupGuestSelection()
        setupConfirmButton()

        updateUi()
    }

    private fun setupHeader() {
        binding.buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
        binding.textTitle.text = getString(R.string.book_experience_title)
        // (añade en strings.xml: <string name="book_experience_title">Book Experience</string>)
    }

    private fun bindExperienceSummary() {
        binding.textExperienceTitle.text = title
        binding.textHostName.text = hostName
        binding.textDepartment.text = department
        binding.textDuration.text = getString(R.string.experience_duration_hours, duration)
        // ej. en strings.xml: "Duration: %1$d hours"

        if (!imageUrl.isNullOrBlank()) {
            binding.imageExperience.load(imageUrl) {
                crossfade(true)
                placeholder(R.drawable.ic_image_placeholder)
            }
        } else {
            binding.imageExperience.setImageResource(R.drawable.ic_image_placeholder)
        }
    }

    private fun setupDateSelection() {
        binding.buttonSelectDates.setOnClickListener {
            val builder = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(getString(R.string.select_dates))

            val picker = builder.build()
            picker.addOnPositiveButtonClickListener { selection ->
                // selection es Pair<Long, Long>?
                val start = selection?.first
                val end = selection?.second
                if (start != null && end != null) {
                    startDateMs = start
                    endDateMs = end
                    updateUi()
                }
            }

            picker.show(parentFragmentManager, "date_range_picker")
        }
    }

    private fun setupGuestSelection() {
        binding.buttonDecreaseGuests.setOnClickListener {
            if (guestCount > 1) {
                guestCount -= 1
                updateUi()
            }
        }

        binding.buttonIncreaseGuests.setOnClickListener {
            guestCount += 1
            updateUi()
        }
    }

    private fun setupConfirmButton() {
        binding.buttonConfirm.setOnClickListener {
            // Aquí luego conectaremos con lógica real de booking.
            // Por ahora simplemente hacemos back o mostramos Snackbar, etc.
            findNavController().popBackStack()
        }
    }

    private fun updateUi() {
        // Texto fechas seleccionadas (card)
        val startText = startDateMs?.let { dateFormatter.format(it) } ?: "Not selected"
        val endText = endDateMs?.let { dateFormatter.format(it) } ?: "Not selected"

        binding.textSelectedDates.text = getString(
            R.string.selected_dates_range,
            startText,
            endText
        )
        // strings.xml: "Start: %1$s   End: %2$s"

        // Guest count y summary
        binding.textGuestCount.text = guestCount.toString()
        binding.textSummaryGuests.text = guestCount.toString()
        binding.textSummaryStartDate.text = if (startDateMs != null) startText else "-"
        binding.textSummaryEndDate.text = if (endDateMs != null) endText else "-"

        // Total = pricePerPerson * guestCount
        val total = pricePerPerson * guestCount
        val totalFormatted = formatCop(total)

        binding.textSummaryTotal.text = totalFormatted

        val canConfirm = startDateMs != null && endDateMs != null && guestCount >= 1

        binding.buttonConfirm.isEnabled = canConfirm
        binding.buttonConfirm.alpha = if (canConfirm) 1f else 0.5f

        binding.buttonConfirm.text = if (canConfirm) {
            getString(R.string.confirm_booking_with_total, totalFormatted)
        } else {
            getString(R.string.confirm_booking)
        }
    }

    private fun formatCop(amount: Long): String {
        // CurrencyInstance("es", "CO") normalmente da algo como "$ 450.000"
        return currencyFormatter.format(amount)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
