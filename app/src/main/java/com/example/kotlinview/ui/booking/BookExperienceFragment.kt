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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder

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

    private val vm: BookExperienceViewModel by viewModels()

    private var isSaving: Boolean = false

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

        viewLifecycleOwner.lifecycleScope.launch {
            vm.state.collectLatest { st ->
                isSaving = st.saving

                // Deshabilitamos el botón durante el guardado
                updateUi()  // para recalcular canConfirm con isSaving

                st.errorMessage?.let { msg ->
                    showErrorDialog(msg)
                }

                if (st.success) {
                    Toast.makeText(requireContext(), "Booking created successfully!", Toast.LENGTH_SHORT).show()
                    vm.consumeSuccess()
                    findNavController().popBackStack()  // volvemos al detalle o al home, según stack
                }
            }
        }

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
        binding.textDuration.text = getString(R.string.experience_duration_days, duration)
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
            // Single date picker: el usuario escoge SOLO la fecha de inicio
            val builder = MaterialDatePicker.Builder.datePicker()
                .setTitleText(getString(R.string.select_start_date))

            val picker = builder.build()

            picker.addOnPositiveButtonClickListener { selection ->
                // selection es un Long? con el start date en millis
                val start = selection
                if (start != null) {
                    startDateMs = start

                    val days = if (duration > 0) duration else 1
                    val millisPerDay = 24L * 60L * 60L * 1000L

                    // end = start + (duration - 1) días
                    endDateMs = start + (days - 1L) * millisPerDay

                    updateUi()
                }
            }

            picker.show(parentFragmentManager, "date_picker_start")
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
            val start = startDateMs ?: return@setOnClickListener
            val end = endDateMs ?: return@setOnClickListener
            val expId = experienceId ?: return@setOnClickListener

            if (guestCount < 1) return@setOnClickListener

            vm.confirmBooking(
                experienceId = expId,
                pricePerPerson = pricePerPerson,
                startAtMs = start,
                endAtMs = end,
                peopleCount = guestCount
            )
        }
    }


    private fun updateUi() {
        val startText = startDateMs?.let { dateFormatter.format(it) } ?: "Not selected"
        val endText = endDateMs?.let { dateFormatter.format(it) } ?: "Not selected"

        binding.textSelectedDates.text = getString(
            R.string.selected_dates_range,
            startText,
            endText
        )

        binding.textGuestCount.text = guestCount.toString()
        binding.textSummaryGuests.text = guestCount.toString()
        binding.textSummaryStartDate.text = if (startDateMs != null) startText else "-"
        binding.textSummaryEndDate.text = if (endDateMs != null) endText else "-"

        val total = pricePerPerson * guestCount
        val totalFormatted = formatCop(total)
        binding.textSummaryTotal.text = totalFormatted

        val hasDates = startDateMs != null && endDateMs != null
        val canConfirm = hasDates && guestCount >= 1 && !isSaving

        binding.buttonConfirm.isEnabled = canConfirm
        binding.buttonConfirm.alpha = if (canConfirm) 1f else 0.5f

        binding.buttonConfirm.text = if (canConfirm) {
            getString(R.string.confirm_booking_with_total, totalFormatted)
        } else {
            getString(R.string.confirm_booking)
        }
    }

    private fun formatCop(amount: Long): String {
        return currencyFormatter.format(amount)
    }

    private fun showErrorDialog(message: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Booking issue")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

}
