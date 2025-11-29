package com.example.kotlinview.data.profile

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class LocalProfile(
    val name: String,
    val email: String,
    val createdAtMs: Long,
    val about: String,
    val languages: List<String>,
    val avgHostRating: Double,
    val photoCachePath: String,
    val photoUrlRemote: String,
    val pendingPhotoPath: String?,
    val hasPendingSync: Boolean
)

interface ProfileRepository {
    val profileFlow: Flow<LocalProfile?>
    suspend fun refreshFromRemote()
    suspend fun saveEdits(name: String, about: String, languages: List<String>)
    suspend fun setPendingPhoto(contentUri: Uri)
    suspend fun syncPendingNow()
}
