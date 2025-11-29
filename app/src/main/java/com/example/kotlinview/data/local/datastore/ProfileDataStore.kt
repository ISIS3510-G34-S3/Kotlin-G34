package com.example.kotlinview.data.local.datastore

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.profileDataStore by preferencesDataStore(name = "profile_store")

data class CachedProfile(
    val docIdEmail: String,
    val name: String,
    val email: String,
    val createdAtMs: Long,
    val about: String,
    val languages: List<String>,
    val avgHostRating: Double,
    val photoUrlRemote: String,
    val photoCachePath: String
)

data class PendingPatch(
    val name: String? = null,
    val about: String? = null,
    val languages: List<String>? = null
) {
    fun isEmpty(): Boolean = name == null && about == null && languages == null
}

data class ProfileLocalState(
    val cached: CachedProfile? = null,
    val pendingPatch: PendingPatch = PendingPatch(),
    val pendingPhotoPath: String? = null
) {
    val hasPendingSync: Boolean = !pendingPatch.isEmpty() || !pendingPhotoPath.isNullOrBlank()
}

private object K {
    // Cached profile subset
    val DOC_ID_EMAIL = stringPreferencesKey("doc_id_email")
    val NAME = stringPreferencesKey("name")
    val EMAIL = stringPreferencesKey("email")
    val CREATED_AT_MS = longPreferencesKey("created_at_ms")
    val ABOUT = stringPreferencesKey("about")
    val LANGUAGES_JSON = stringPreferencesKey("languages_json")
    val AVG_HOST_RATING = doublePreferencesKey("avg_host_rating")
    val PHOTO_URL_REMOTE = stringPreferencesKey("photo_url_remote")
    val PHOTO_CACHE_PATH = stringPreferencesKey("photo_cache_path")

    // Pending sync
    val PENDING_PATCH_JSON = stringPreferencesKey("pending_patch_json")
    val PENDING_PHOTO_PATH = stringPreferencesKey("pending_photo_path")
}

class ProfileStore(private val ctx: Context) {

    fun stateFlow() = ctx.profileDataStore.data.map { p ->
        val docIdEmail = p[K.DOC_ID_EMAIL].orEmpty()
        val cached = if (docIdEmail.isNotBlank()) {
            CachedProfile(
                docIdEmail = docIdEmail,
                name = p[K.NAME].orEmpty(),
                email = p[K.EMAIL].orEmpty(),
                createdAtMs = p[K.CREATED_AT_MS] ?: 0L,
                about = p[K.ABOUT].orEmpty(),
                languages = decodeLanguages(p[K.LANGUAGES_JSON].orEmpty()),
                avgHostRating = p[K.AVG_HOST_RATING] ?: 0.0,
                photoUrlRemote = p[K.PHOTO_URL_REMOTE].orEmpty(),
                photoCachePath = p[K.PHOTO_CACHE_PATH].orEmpty()
            )
        } else null

        val pendingPatch = decodePendingPatch(p[K.PENDING_PATCH_JSON].orEmpty())
        val pendingPhotoPath = p[K.PENDING_PHOTO_PATH]?.takeIf { it.isNotBlank() }

        ProfileLocalState(
            cached = cached,
            pendingPatch = pendingPatch,
            pendingPhotoPath = pendingPhotoPath
        )
    }

    suspend fun readState(): ProfileLocalState = stateFlow().first()

    suspend fun writeCachedProfile(profile: CachedProfile) {
        ctx.profileDataStore.edit { p ->
            p[K.DOC_ID_EMAIL] = profile.docIdEmail
            p[K.NAME] = profile.name
            p[K.EMAIL] = profile.email
            p[K.CREATED_AT_MS] = profile.createdAtMs
            p[K.ABOUT] = profile.about
            p[K.LANGUAGES_JSON] = encodeLanguages(profile.languages)
            p[K.AVG_HOST_RATING] = profile.avgHostRating
            p[K.PHOTO_URL_REMOTE] = profile.photoUrlRemote
            p[K.PHOTO_CACHE_PATH] = profile.photoCachePath
        }
    }

    suspend fun updateCachedFields(
        name: String? = null,
        about: String? = null,
        languages: List<String>? = null,
        avgHostRating: Double? = null,
        photoUrlRemote: String? = null,
        photoCachePath: String? = null,
        createdAtMs: Long? = null,
        email: String? = null,
        docIdEmail: String? = null
    ) {
        ctx.profileDataStore.edit { p ->
            if (docIdEmail != null) p[K.DOC_ID_EMAIL] = docIdEmail
            if (name != null) p[K.NAME] = name
            if (email != null) p[K.EMAIL] = email
            if (createdAtMs != null) p[K.CREATED_AT_MS] = createdAtMs
            if (about != null) p[K.ABOUT] = about
            if (languages != null) p[K.LANGUAGES_JSON] = encodeLanguages(languages)
            if (avgHostRating != null) p[K.AVG_HOST_RATING] = avgHostRating
            if (photoUrlRemote != null) p[K.PHOTO_URL_REMOTE] = photoUrlRemote
            if (photoCachePath != null) p[K.PHOTO_CACHE_PATH] = photoCachePath
        }
    }

    suspend fun mergePendingPatch(newPatch: PendingPatch) {
        val current = readState().pendingPatch
        val merged = PendingPatch(
            name = newPatch.name ?: current.name,
            about = newPatch.about ?: current.about,
            languages = newPatch.languages ?: current.languages
        )
        ctx.profileDataStore.edit { p ->
            p[K.PENDING_PATCH_JSON] = encodePendingPatch(merged)
        }
    }

    suspend fun clearPendingPatch() {
        ctx.profileDataStore.edit { p -> p[K.PENDING_PATCH_JSON] = "" }
    }

    suspend fun setPendingPhotoPath(path: String) {
        ctx.profileDataStore.edit { p -> p[K.PENDING_PHOTO_PATH] = path }
    }

    suspend fun clearPendingPhotoPath() {
        ctx.profileDataStore.edit { p -> p.remove(K.PENDING_PHOTO_PATH) }
    }

    private fun encodeLanguages(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun decodeLanguages(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<String>(arr.length())
            for (i in 0 until arr.length()) out.add(arr.optString(i))
            out
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun encodePendingPatch(patch: PendingPatch): String {
        if (patch.isEmpty()) return ""
        val o = JSONObject()
        if (patch.name != null) o.put("name", patch.name)
        if (patch.about != null) o.put("about", patch.about)
        if (patch.languages != null) o.put("languages", JSONArray(patch.languages))
        return o.toString()
    }

    private fun decodePendingPatch(json: String): PendingPatch {
        if (json.isBlank()) return PendingPatch()
        return try {
            val o = JSONObject(json)
            val name = if (o.has("name")) o.optString("name") else null
            val about = if (o.has("about")) o.optString("about") else null
            val languages = if (o.has("languages")) {
                val arr = o.optJSONArray("languages")
                val out = ArrayList<String>()
                if (arr != null) {
                    for (i in 0 until arr.length()) out.add(arr.optString(i))
                }
                out
            } else null
            PendingPatch(name = name, about = about, languages = languages)
        } catch (_: Throwable) {
            PendingPatch()
        }
    }
}
