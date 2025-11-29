package com.example.kotlinview.data.profile

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.kotlinview.data.local.datastore.CachedProfile
import com.example.kotlinview.data.local.datastore.PendingPatch
import com.example.kotlinview.data.local.datastore.ProfileStore
import com.example.kotlinview.data.profile.sync.ProfileSyncWorker
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

class FirebaseProfileRepository(
    private val appContext: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val store: ProfileStore
) : ProfileRepository {

    override val profileFlow: Flow<LocalProfile?> =
        store.stateFlow().map { st ->
            val c = st.cached ?: return@map null
            LocalProfile(
                name = c.name,
                email = c.email,
                createdAtMs = c.createdAtMs,
                about = c.about,
                languages = c.languages,
                avgHostRating = c.avgHostRating,
                photoCachePath = c.photoCachePath,
                photoUrlRemote = c.photoUrlRemote,
                pendingPhotoPath = st.pendingPhotoPath,
                hasPendingSync = st.hasPendingSync
            )
        }

    override suspend fun refreshFromRemote() {
        val email = auth.currentUser?.email ?: return
        val doc = db.collection("users").document(email).get().await()
        if (!doc.exists()) return

        val d = doc.data ?: return
        val remoteName = (d["name"] as? String).orEmpty()
        val remoteEmail = (d["email"] as? String).orEmpty().ifBlank { email }
        val remoteAbout = (d["about"] as? String).orEmpty()
        val remoteLanguages = (d["languages"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList()
        val remoteAvg = (d["avgHostRating"] as? Number)?.toDouble() ?: 0.0
        val remotePhotoUrl = (d["photoURL"] as? String).orEmpty()
        val createdAtMs = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L

        val st = store.readState()

        // If pending photo path exists but file is gone, clear it (prevents stuck pending banner)
        st.pendingPhotoPath?.let { path ->
            if (path.isNotBlank() && !java.io.File(path).exists()) {
                store.clearPendingPhotoPath()
            }
        }

        // If pending patch matches remote, clear it (self-heals "stuck pending" UI)
        val pending = st.pendingPatch
        if (!pending.isEmpty()) {
            val matches =
                (pending.name == null || pending.name == remoteName) &&
                        (pending.about == null || pending.about == remoteAbout) &&
                        (pending.languages == null || pending.languages!!.toSet() == remoteLanguages.toSet())

            if (matches) {
                store.clearPendingPatch()
            }
        }

        // Re-read after potential clears so merge logic uses the latest local state
        val st2 = store.readState()
        val pending2 = st2.pendingPatch
        val pendingPhotoPath2 = st2.pendingPhotoPath

        val nameToSave = pending2.name ?: remoteName
        val aboutToSave = pending2.about ?: remoteAbout
        val langsToSave = pending2.languages ?: remoteLanguages

        val cacheFile = cachePhotoFile(email)
        val cachePath = cacheFile.absolutePath

        // Only refresh cached.jpg from remote if we DON'T have a pending chosen photo
        if (pendingPhotoPath2.isNullOrBlank()) {
            val lastUrl = st2.cached?.photoUrlRemote.orEmpty()
            val shouldDownload = remotePhotoUrl.isNotBlank() &&
                    (lastUrl != remotePhotoUrl || !cacheFile.exists())

            if (shouldDownload) {
                downloadToFile(remotePhotoUrl, cacheFile)
            }
        }

        store.writeCachedProfile(
            com.example.kotlinview.data.local.datastore.CachedProfile(
                docIdEmail = email,
                name = nameToSave,
                email = remoteEmail,
                createdAtMs = createdAtMs,
                about = aboutToSave,
                languages = langsToSave,
                avgHostRating = remoteAvg,
                photoUrlRemote = remotePhotoUrl,
                photoCachePath = cachePath
            )
        )
    }


    override suspend fun saveEdits(name: String, about: String, languages: List<String>) {
        val email = auth.currentUser?.email ?: return

        // 1) Optimistic local update (always)
        store.updateCachedFields(
            docIdEmail = email,
            name = name,
            about = about,
            languages = languages
        )

        val st = store.readState()
        val online = com.example.kotlinview.core.NetworkUtil.isOnline(appContext)

        // If there is already pending work, keep using our queue to remain consistent.
        val alreadyPending = st.hasPendingSync

        // 2) If online and nothing pending, try remote directly WITHOUT setting pending => no banner flash
        if (online && !alreadyPending) {
            val ok = runCatching {
                val patch = hashMapOf<String, Any>(
                    "name" to name,
                    "about" to about,
                    "languages" to languages,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                )
                db.collection("users").document(email).update(patch).await()
            }.isSuccess

            if (ok) return

            // If it failed for any reason, fall through to queue pending
        }

        // 3) Offline OR already pending OR remote failed -> persist pending patch and enqueue worker
        store.mergePendingPatch(
            com.example.kotlinview.data.local.datastore.PendingPatch(
                name = name,
                about = about,
                languages = languages
            )
        )

        enqueueSync()
    }

    override suspend fun setPendingPhoto(contentUri: Uri) {
        val email = auth.currentUser?.email ?: return

        val pendingFile = pendingPhotoFile(email)
        copyUriToFile(appContext, contentUri, pendingFile)

        // Save pending photo path so UI can show it immediately (offline-safe)
        store.setPendingPhotoPath(pendingFile.absolutePath)

        // Enqueue worker to upload when connected
        enqueueSync()
    }

    override suspend fun syncPendingNow() {
        // Used by Worker (and could be used by UI if you want).
        val email = auth.currentUser?.email ?: return
        val uid = auth.currentUser?.uid ?: ""

        val st = store.readState()

        // 1) Upload pending photo if any
        val pendingPhotoPath = st.pendingPhotoPath
        if (!pendingPhotoPath.isNullOrBlank()) {
            val src = File(pendingPhotoPath)
            if (src.exists()) {
                val remotePath = "profile_pic/$uid/profile_${System.currentTimeMillis()}.jpg"
                val ref = storage.reference.child(remotePath)
                ref.putFile(Uri.fromFile(src)).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                db.collection("users").document(email).update(
                    mapOf(
                        "photoURL" to downloadUrl,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                // Promote pending -> cached (guaranteed offline)
                val cacheFile = cachePhotoFile(email)
                src.copyTo(cacheFile, overwrite = true)
                store.updateCachedFields(photoUrlRemote = downloadUrl, photoCachePath = cacheFile.absolutePath)

                store.clearPendingPhotoPath()
            } else {
                store.clearPendingPhotoPath()
            }
        }

        // 2) Push pending patch if any
        val patch = st.pendingPatch
        if (!patch.isEmpty()) {
            val update = HashMap<String, Any>()
            patch.name?.let { update["name"] = it }
            patch.about?.let { update["about"] = it }
            patch.languages?.let { update["languages"] = it }
            update["updatedAt"] = FieldValue.serverTimestamp()

            db.collection("users").document(email).update(update).await()
            store.clearPendingPatch()
        }
    }

    private fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val req = OneTimeWorkRequestBuilder<ProfileSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(appContext)
            .enqueueUniqueWork("profile_sync", ExistingWorkPolicy.KEEP, req)
    }

    private fun cachePhotoFile(email: String): File {
        val dir = File(appContext.filesDir, "profile_pic/$email")
        dir.mkdirs()
        return File(dir, "cached.jpg")
    }

    private fun pendingPhotoFile(email: String): File {
        val dir = File(appContext.filesDir, "profile_pic/$email")
        dir.mkdirs()
        return File(dir, "pending.jpg")
    }

    private suspend fun downloadToFile(url: String, outFile: File) = withContext(Dispatchers.IO) {
        runCatching {
            URL(url).openStream().use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    private suspend fun copyUriToFile(ctx: Context, uri: Uri, to: File) = withContext(Dispatchers.IO) {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            to.outputStream().use { output -> input.copyTo(output) }
        }
    }
}
