package com.example.kotlinview.core

import android.content.Context
import android.util.Log
import com.example.kotlinview.data.experiences.ExperiencesRepository
import com.example.kotlinview.data.experiences.FirestoreExperiencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.SetOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.firebase.auth.FirebaseAuth
import com.example.kotlinview.data.auth.AuthRemoteDataSource
import com.example.kotlinview.data.auth.AuthRepository
import com.example.kotlinview.data.user.UserRepository
import com.example.kotlinview.data.user.FirestoreUserRepository
import com.example.kotlinview.core.SessionManager
import com.example.kotlinview.model.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// --- Local storage (added) ---
import com.example.kotlinview.data.local.ExperienceLocalStore
import com.example.kotlinview.data.local.ExperienceLocalStoreImpl
import com.example.kotlinview.data.local.api.ExperienceLocalApi
import com.example.kotlinview.data.local.api.ExperienceLocalApiImpl
import com.tencent.mmkv.MMKV

import com.example.kotlinview.data.local.datastore.ProfileStore
import com.example.kotlinview.data.profile.ProfileRepository
import com.example.kotlinview.data.profile.FirebaseProfileRepository
import com.google.firebase.storage.FirebaseStorage

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

    // --- App context holder (added) ---
    @Volatile private var appCtx: Context? = null

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        if (appCtx == null) {
            val app = context.applicationContext   // non-null
            appCtx = app
            MMKV.initialize(app)                   // ✅ no type mismatch
        }
    }

    // Singleton Firestore
    private var firestoreInstance: FirebaseFirestore? = null
    fun provideFirestore(): FirebaseFirestore {
        val existing = firestoreInstance
        if (existing != null) return existing

        val db = FirebaseFirestore.getInstance()
        firestoreInstance = db
        return db
    }

    fun provideFirebaseAuth(): FirebaseAuth {
        return authInstance ?: FirebaseAuth.getInstance().also { authInstance = it }
    }

    fun provideUserRepository(): UserRepository =
        userRepoInstance ?: FirestoreUserRepository(provideFirestore()).also { userRepoInstance = it }

    private var experiencesRepoInstance: ExperiencesRepository? = null
    fun provideExperiencesRepository(): ExperiencesRepository {
        return experiencesRepoInstance
            ?: FirestoreExperiencesRepository(provideFirestore()).also { experiencesRepoInstance = it }
    }

    /** Single Firestore instance (non-KTX). */
    val firestore: FirebaseFirestore by lazy {
        // Enable Firestore debug logs in debug builds
        try {
            FirebaseFirestore.setLoggingEnabled(true)
            Log.d(TAG, "Firestore logging enabled")
        } catch (t: Throwable) {
            Log.w(TAG, "Could not enable Firestore logging", t)
        }

        val instance = FirebaseFirestore.getInstance()

        val settings = FirebaseFirestoreSettings.Builder()
            // Customize if needed (persistence/cache/etc.)
            .build()
        instance.firestoreSettings = settings

        Log.d(TAG, "Firestore instance created; settings applied")
        instance
    }

    private var authInstance: FirebaseAuth? = null
    private var authRepoInstance: AuthRepository? = null
    private var userRepoInstance: UserRepository? = null

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

    /**
     * Increment monthly usage for a feature (e.g., "map_feature") in collection `feature_usage_monthly`.
     * Doc ID: {featureKey}_{YYYY-MM}, fields: featureKey, date ("YYYY-MM"), count (number).
     *
     * This is minSdk 24–safe (uses SimpleDateFormat instead of java.time).
     */
    fun incrementFeatureUsage(featureKey: String) {
        val monthStr = try {
            val sdf = SimpleDateFormat("yyyy-MM", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.format(Date())
        } catch (t: Throwable) {
            // Very defensive: fallback without forcing timezone if something weird happens
            SimpleDateFormat("yyyy-MM", Locale.US).format(Date())
        }

        val docId = "${featureKey}_$monthStr"
        val docRef = firestore.collection("feature_usage_monthly").document(docId)

        firestore.runTransaction { tx ->
            val snap = tx.get(docRef)
            val current = snap.getLong("count") ?: 0L
            val data = hashMapOf(
                "featureKey" to featureKey,
                "date" to monthStr,
                "count" to current + 1L
            )
            tx.set(docRef, data, SetOptions.merge())
            null
        }.addOnSuccessListener {
            Log.d(TAG, "incrementFeatureUsage($featureKey) -> $docId incremented")
        }.addOnFailureListener { e ->
            Log.w(TAG, "incrementFeatureUsage($featureKey) failed", e)
        }
    }

    fun provideAuthRepository(): AuthRepository {
        return authRepoInstance ?: AuthRepository(
            AuthRemoteDataSource(provideFirebaseAuth())
        ).also { authRepoInstance = it }
    }

    suspend fun preloadUserProfileIfLogged(): User? = withContext(Dispatchers.IO) {
        val firebaseUser = provideFirebaseAuth().currentUser ?: return@withContext null
        val email = firebaseUser.email ?: return@withContext null

        val repo = provideUserRepository()

        runCatching { repo.setLastSignInNow(email) }

        val user = repo.getByEmail(email)
        SessionManager.setUser(user)

        // Warm local profile cache for offline viewing (best-effort)
        runCatching {
            val ctx = appCtx
            if (ctx != null) {
                provideProfileRepository(ctx).refreshFromRemote()
            }
        }

        return@withContext user
    }

    fun warmProfileCacheAsync() {
        val ctx = appCtx ?: return
        appScope.launch {
            runCatching {
                // This must call your Profile repository refresh method that downloads and stores cached.jpg
                provideProfileRepository(ctx).refreshFromRemote()
            }
        }
    }

    // --- Local store singleton (Room + Files) ---
    @Volatile private var experienceLocalStore: ExperienceLocalStore? = null
    fun provideExperienceLocalStore(context: Context): ExperienceLocalStore {
        val ctx = appCtx ?: context.applicationContext
        return experienceLocalStore ?: synchronized(this) {
            experienceLocalStore ?: ExperienceLocalStoreImpl(ctx).also { experienceLocalStore = it }
        }
    }

    // --- Black-box API singleton for catalogue teammates ---
    @Volatile private var experienceLocalApi: ExperienceLocalApi? = null
    fun provideExperienceLocalApi(context: Context): ExperienceLocalApi {
        val ctx = appCtx ?: context.applicationContext
        return experienceLocalApi ?: synchronized(this) {
            experienceLocalApi ?: ExperienceLocalApiImpl(ctx).also { experienceLocalApi = it }
        }
    }

    @Volatile private var profileRepoInstance: ProfileRepository? = null

    fun provideProfileRepository(context: Context): ProfileRepository {
        val ctx = appCtx ?: context.applicationContext
        return profileRepoInstance ?: synchronized(this) {
            profileRepoInstance ?: FirebaseProfileRepository(
                appContext = ctx,
                db = provideFirestore(),
                auth = provideFirebaseAuth(),
                storage = FirebaseStorage.getInstance(),
                store = ProfileStore(ctx)
            ).also { profileRepoInstance = it }
        }
    }

}
