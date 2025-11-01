package com.example.kotlinview.analytics

import android.content.Context
import android.util.Log
import com.example.kotlinview.core.ServiceLocator
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object DeviceDistributionReporter {

    private const val TAG = "DeviceDistribution"
    private const val PREFS = "app_prefs"
    private const val KEY_REPORTED = "device_distribution_reported_once"

    /**
     * Increments device_distribution/{device_YYYY-MM} exactly once per install.
     *
     * - Uses SharedPreferences flag to prevent double counting.
     * - Month key is computed in UTC as "yyyy-MM".
     * - Document ID is sanitized (no slashes; spaces -> underscores).
     */
    fun recordInstallIfNeeded(
        appContext: Context,
        deviceLabel: String,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_REPORTED, false)) {
            onComplete?.invoke(true)
            return
        }

        // Month string in UTC, aligned with your other counters.
        val monthStr = try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.format(Date())
        } catch (_: Throwable) {
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        }

        // Sanitize ID parts to be safe for a Firestore document ID
        val safeDevice = deviceLabel
            .replace("/", "_")
            .replace("\\s+".toRegex(), "_")
            .take(120) // be conservative

        val docId = "${safeDevice}_$monthStr"

        val db = ServiceLocator.provideFirestore()
        val docRef = db.collection("device_distribution").document(docId)

        db.runTransaction { tx ->
            val snap = tx.get(docRef)
            val current = snap.getLong("count") ?: 0L
            val data = hashMapOf(
                "device" to deviceLabel,
                "date" to monthStr,
                "count" to (current + 1L)
            )
            tx.set(docRef, data, SetOptions.merge())
            null
        }.addOnSuccessListener {
            // Mark as reported ONLY on success
            prefs.edit().putBoolean(KEY_REPORTED, true).apply()
            Log.d(TAG, "Reported device_distribution for $docId")
            onComplete?.invoke(true)
        }.addOnFailureListener { e ->
            Log.w(TAG, "Failed to report device_distribution for $docId", e)
            onComplete?.invoke(false)
        }
    }
}
