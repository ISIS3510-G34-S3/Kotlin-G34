package com.example.kotlinview.data.experiences

sealed class BookingResult {
    object Success : BookingResult()
    data class Failure(val reason: BookingError) : BookingResult()
}

enum class BookingError {
    OVER_GROUP_SIZE,
    DATES_NOT_AVAILABLE,
    NO_TRAVELER_ID,

    NETWORK,
    UNKNOWN
}
