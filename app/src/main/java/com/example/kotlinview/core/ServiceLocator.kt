package com.example.kotlinview.core

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.example.kotlinview.data.experiences.ExperiencesRepository
import com.example.kotlinview.data.experiences.FirestoreExperiencesRepository

/**
 * Super-lightweight DI for the app.
 *
 * Uses NON-KTX Firestore:
 *   implementation(platform("com.google.firebase:firebase-bom:34.3.0"))
 *   implementation("com.google.firebase:firebase-firestore")
 *   implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
 */
object ServiceLocator {

    private const val TAG = "ServiceLocator"

    /** Single Firestore instance (non-KTX). */
    val firestore: FirebaseFirestore by lazy {
        // Verbose Firestore debug logs to Logcat (Network, cache, retries, etc.)
        // Note: This is safe in debug; consider disabling for release builds.
        try {
            FirebaseFirestore.setLoggingEnabled(true)
            Log.d(TAG, "Firestore logging enabled")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not enable Firestore logging", t)
        }

        val instance = FirebaseFirestore.getInstance()

        // Optional: tweak settings if you want (e.g., offline persistence)
        val settings = FirebaseFirestoreSettings.Builder()
            // .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build()) // example
            // .setSslEnabled(true) // default true
            .build()
        instance.firestoreSettings = settings

        Log.d(TAG, "Firestore instance created; settings applied")
        instance
    }

    @Volatile private var _overrideRepo: ExperiencesRepository? = null
    @Volatile private var _realRepo: ExperiencesRepository? = null

    /**
     * Repository used by the Map feature (and reusable by others).
     * If a test override is present, it wins. Otherwise we lazily create the real repo once.
     */
    val experiencesRepository: ExperiencesRepository
        get() = _overrideRepo ?: synchronized(this) {
            _overrideRepo ?: (_realRepo ?: FirestoreExperiencesRepository(firestore).also {
                _realRepo = it
                Log.d(TAG, "FirestoreExperiencesRepository created")
            })
        }

    /** Test hook: inject a fake repository. */
    fun overrideExperiencesRepositoryForTests(fake: ExperiencesRepository) {
        Log.d(TAG, "overrideExperiencesRepositoryForTests() called with ${fake::class.java.simpleName}")
        _overrideRepo = fake
    }

    /** Clear the test override. */
    fun clearOverrides() {
        Log.d(TAG, "clearOverrides() called")
        _overrideRepo = null
    }

    /** Optional: drop the cached real repo (e.g., if you want to recreate it). */
    fun resetRealRepository() {
        Log.d(TAG, "resetRealRepository() called")
        _realRepo = null
    }

    /** Helper to print Firestore settings to Logcat for diagnostics. */
    fun dumpFirestoreConfig() {
        val s = firestore.firestoreSettings
        Log.d(TAG, "Firestore settings -> sslEnabled=${s.isSslEnabled}")
    }
}
