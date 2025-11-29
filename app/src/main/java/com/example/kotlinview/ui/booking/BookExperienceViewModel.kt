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
import com.example.kotlinview.data.experiences.BookingRetryManager
import com.example.kotlinview.data.experiences.PendingBooking

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

                is BookingResult.Failure -> {
                    when (result.reason) {
                        BookingError.OVER_GROUP_SIZE ->
                            BookExperienceUiState(
                                saving = false,
                                errorMessage = "The selected number of travelers exceeds the maximum group size for this experience.",
                                success = false
                            )

                        BookingError.DATES_NOT_AVAILABLE ->
                            BookExperienceUiState(
                                saving = false,
                                errorMessage = "Those dates are not available. Please choose another date range.",
                                success = false
                            )

                        BookingError.NO_TRAVELER_ID ->
                            BookExperienceUiState(
                                saving = false,
                                errorMessage = "You must be logged in to book this experience.",
                                success = false
                            )

                        BookingError.NETWORK -> {
                            // Guardamos la booking en caché local para reintentar luego
                            val pending = PendingBooking(
                                experienceId = experienceId,
                                travelerEmail = userEmail,
                                startAtMs = startAtMs,
                                endAtMs = endAtMs,
                                peopleCount = peopleCount,
                                amountCOP = totalAmount
                            )
                            BookingRetryManager.enqueuePending(pending)

                            BookExperienceUiState(
                                saving = false,
                                errorMessage = "We couldn't reach the server. Your booking was saved and will be sent automatically when the connection is restored.",
                                success = false
                            )
                        }

                        BookingError.UNKNOWN ->
                            BookExperienceUiState(
                                saving = false,
                                errorMessage = "We couldn't create the booking. Please try again.",
                                success = false
                            )
                    }
                }
            }
        }
    }


    fun consumeSuccess() {
        if (_state.value.success) {
            _state.value = _state.value.copy(success = false)
        }
    }
}
