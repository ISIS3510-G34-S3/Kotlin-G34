package com.example.kotlinview.data.experiences

data class PendingBooking(
    val experienceId: String,
    val travelerEmail: String,
    val startAtMs: Long,
    val endAtMs: Long,
    val peopleCount: Int,
    val amountCOP: Long
)
