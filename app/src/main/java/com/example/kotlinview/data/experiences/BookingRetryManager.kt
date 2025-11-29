package com.example.kotlinview.data.experiences

import android.content.Context
import android.util.Log
import com.example.kotlinview.core.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

object BookingRetryManager {

    private const val PREFS_NAME = "booking_retry_prefs"
    private const val KEY_PENDING = "pending_bookings"

    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val repo by lazy { ServiceLocator.provideExperiencesRepository() }

    @Volatile
    private var started = false

    fun init(context: Context) {
        if (started) return
        appContext = context.applicationContext
        started = true
        startRetryLoop()
    }

        fun enqueuePending(booking: PendingBooking) {
            scope.launch {
                try {
                    val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    val current = prefs.getString(KEY_PENDING, "[]") ?: "[]"
                    val array = JSONArray(current)

                    val obj = JSONObject().apply {
                        put("experienceId", booking.experienceId)
                        put("travelerEmail", booking.travelerEmail)
                        put("startAtMs", booking.startAtMs)
                        put("endAtMs", booking.endAtMs)
                        put("peopleCount", booking.peopleCount)
                        put("amountCOP", booking.amountCOP)
                    }

                    array.put(obj)
                    prefs.edit().putString(KEY_PENDING, array.toString()).apply()

                    Log.d("BookingRetryManager", "Enqueued pending booking: $obj")
                } catch (e: Exception) {
                    Log.e("BookingRetryManager", "Error enqueuing pending booking", e)
                }
            }
        }

    private fun startRetryLoop() {
        scope.launch {
            while (true) {
                try {
                    trySendPendingBookings()
                } catch (e: Exception) {
                    Log.e("BookingRetryManager", "Error in retry loop", e)
                }
                delay(30_000L) // 30 segundos
            }
        }
    }

    private suspend fun trySendPendingBookings() {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getString(KEY_PENDING, "[]") ?: "[]"
        val array = JSONArray(current)

        if (array.length() == 0) return

        val remaining = JSONArray()
        Log.d("BookingRetryManager", "Found ${array.length()} pending bookings")

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val pending = PendingBooking(
                experienceId = obj.getString("experienceId"),
                travelerEmail = obj.getString("travelerEmail"),
                startAtMs = obj.getLong("startAtMs"),
                endAtMs = obj.getLong("endAtMs"),
                peopleCount = obj.getInt("peopleCount"),
                amountCOP = obj.getLong("amountCOP")
            )

            val result = repo.createBooking(
                experienceId = pending.experienceId,
                travelerEmail = pending.travelerEmail,
                startAtMs = pending.startAtMs,
                endAtMs = pending.endAtMs,
                peopleCount = pending.peopleCount,
                amountCOP = pending.amountCOP
            )

            when (result) {
                is BookingResult.Success -> {

                    Log.d("BookingRetryManager", "Pending booking sent successfully: $pending")
                }
                is BookingResult.Failure -> {
                    Log.d("BookingRetryManager", "Failed to send pending booking: reason=${result.reason}")
                    if (result.reason == BookingError.NETWORK) {

                        remaining.put(obj)
                    } else {
                        // Logical error. Discarded
                    }
                }
            }
        }

        prefs.edit().putString(KEY_PENDING, remaining.toString()).apply()
        Log.d("BookingRetryManager", "Remaining pending bookings: ${remaining.length()}")
    }
}
