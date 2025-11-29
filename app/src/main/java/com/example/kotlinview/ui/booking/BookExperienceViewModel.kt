package com.example.kotlinview.ui.booking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.core.SessionManager
import com.example.kotlinview.data.experiences.BookingError
import com.example.kotlinview.data.experiences.BookingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth

data class BookExperienceUiState(
    val saving: Boolean = false,
    val errorMessage: String? = null,
    val success: Boolean = false
)

class BookExperienceViewModel : ViewModel() {

    private val repo = ServiceLocator.provideExperiencesRepository()

    private val _state = MutableStateFlow(BookExperienceUiState())
    val state: StateFlow<BookExperienceUiState> = _state

    fun confirmBooking(
        experienceId: String,
        pricePerPerson: Long,
        startAtMs: Long,
        endAtMs: Long,
        peopleCount: Int
    ) {

        val emailFromSession = SessionManager.currentUser.value?.email
            ?.takeIf { it.isNotBlank() }


        val emailFromFirebase = FirebaseAuth.getInstance().currentUser?.email
            ?.takeIf { it.isNotBlank() }

        val userEmail = emailFromSession ?: emailFromFirebase

        if (userEmail == null) {
            _state.value = BookExperienceUiState(
                saving = false,
                errorMessage = "You must be logged in to book this experience."
            )
            return
        }

        val totalAmount = pricePerPerson * peopleCount

        // 🔴 IMPORTANTE: todo lo de Firestore en IO
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = BookExperienceUiState(
                saving = true,
                errorMessage = null,
                success = false
            )

            val result = repo.createBooking(
                experienceId = experienceId,
                travelerEmail = userEmail,
                startAtMs = startAtMs,
                endAtMs = endAtMs,
                peopleCount = peopleCount,
                amountCOP = totalAmount
            )

            _state.value = when (result) {
                is BookingResult.Success ->
                    BookExperienceUiState(saving = false, success = true)

                is BookingResult.Failure ->
                    BookExperienceUiState(
                        saving = false,
                        errorMessage = when (result.reason) {
                            BookingError.OVER_GROUP_SIZE ->
                                "The selected number of travelers exceeds the maximum group size for this experience."
                            BookingError.DATES_NOT_AVAILABLE ->
                                "Those dates are not available. Please choose another date range."
                            BookingError.NO_TRAVELER_ID ->
                                "You must be logged in to book this experience."
                            BookingError.UNKNOWN ->
                                "We couldn't create the booking. Please try again."

                            BookingError.NETWORK -> TODO()
                        },
                        success = false
                    )
            }
        }
    }


    fun consumeSuccess() {
        if (_state.value.success) {
            _state.value = _state.value.copy(success = false)
        }
    }
}
