package com.example.kotlinview.data.local

import android.content.Context
import com.example.kotlinview.data.local.datastore.PolicyStore
import com.example.kotlinview.data.local.kv.MmkvBuckets
import com.example.kotlinview.data.local.room.AppDatabase
import com.example.kotlinview.data.local.room.ExperienceEntity
import com.example.kotlinview.data.local.files.LocalImageStore
import com.example.kotlinview.data.map.ExperienceDtoMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

interface ExperienceLocalStore {
    suspend fun getAll(): List<ExperienceDtoMap>
    suspend fun getByIds(ids: List<String>): List<ExperienceDtoMap>
    suspend fun getNearest(lat: Double, lng: Double, k: Int): List<ExperienceDtoMap>

    /**
     * Persist only the first [maxToPersist] items into Room (SSOT).
     * Also refresh the KV nearest bucket if [currentLat] & [currentLng] are provided,
     * and optionally prefetch original images (for catalogue) if [prefetchImages] is true.
     */
    suspend fun upsertFromRemote(
        items: List<ExperienceDtoMap>,
        maxToPersist: Int,
        currentLat: Double? = null,
        currentLng: Double? = null,
        prefetchImages: Boolean = false
    )

    // KV bucket helpers (MMKV)
    suspend fun getBucketTopIds(bucketKey: String): List<String>
    suspend fun setBucketTopIds(bucketKey: String, ids: List<String>, nowMs: Long)

    // Policy/meta
    suspend fun readPolicyMeta(): com.example.kotlinview.data.local.datastore.PolicyMeta
    suspend fun updatePolicyMeta(mut: (com.example.kotlinview.data.local.datastore.PolicyMeta) -> com.example.kotlinview.data.local.datastore.PolicyMeta)

    // Local image access
    fun findLocalImage(expId: String): java.io.File?
    suspend fun prefetchLocalImages(items: List<ExperienceDtoMap>, maxToPersist: Int)
}

class ExperienceLocalStoreImpl(
    private val context: Context
) : ExperienceLocalStore {

    private val db by lazy { AppDatabase.get(context).experiences() }
    private val policy by lazy { PolicyStore(context) }
    private val images by lazy { LocalImageStore(context) }

    override suspend fun getAll(): List<ExperienceDtoMap> = withContext(Dispatchers.IO) {
        db.getAll().map { it.toDto() }
    }

    override suspend fun getByIds(ids: List<String>): List<ExperienceDtoMap> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        db.getByIds(ids).map { it.toDto() }
    }

    override suspend fun getNearest(lat: Double, lng: Double, k: Int): List<ExperienceDtoMap> = withContext(Dispatchers.IO) {
        val all = db.getAll().map { it.toDto() }
        all.sortedBy { haversineKm(lat, lng, it.latitude ?: 0.0, it.longitude ?: 0.0) }
            .take(k)
    }

    override suspend fun upsertFromRemote(
        items: List<ExperienceDtoMap>,
        maxToPersist: Int,
        currentLat: Double?,
        currentLng: Double?,
        prefetchImages: Boolean
    ) = withContext(Dispatchers.IO) {
        if (items.isEmpty() || maxToPersist <= 0) return@withContext

        val toPersist = items.take(maxToPersist)
        val entities = toPersist.map { ExperienceEntity.fromDto(it) }
        db.upsertAll(entities)

        val now = System.currentTimeMillis()
        // Update DataStore minimal fields
        policy.update { meta ->
            meta.copy(
                lastSyncMs = now,
                lastRemoteCount = toPersist.size.toLong()
            )
        }

        // Update KV nearest bucket if location given
        if (currentLat != null && currentLng != null) {
            val bucketKey = bucketKeyFor(currentLat, currentLng)
            val top5 = toPersist
                .sortedBy { haversineKm(currentLat, currentLng, it.latitude ?: 0.0, it.longitude ?: 0.0) }
                .take(5)
                .mapNotNull { it.id }
            MmkvBuckets.writeTopIds(bucketKey, top5, now)
            policy.update { meta ->
                meta.copy(
                    lastNearestRefreshMs = now,
                    lastLocationLat = currentLat,
                    lastLocationLng = currentLng
                )
            }
        }

        // Optional: prefetch original images for catalogue fallback
        if (prefetchImages) {
            // Only first image per exp; keep this simple
            toPersist.forEach { dto ->
                val id = dto.id ?: return@forEach
                val firstUrl = dto.images?.firstOrNull() ?: return@forEach
                images.saveOriginalIfNeeded(id, firstUrl)
            }
            images.enforceCountCap(maxCount = 12) // keep it small; adjust to your liking
        }
    }

    override suspend fun getBucketTopIds(bucketKey: String): List<String> =
        MmkvBuckets.readTopIds(bucketKey)

    override suspend fun setBucketTopIds(bucketKey: String, ids: List<String>, nowMs: Long) {
        MmkvBuckets.writeTopIds(bucketKey, ids, nowMs)
    }

    override suspend fun readPolicyMeta() = policy.read()
    override suspend fun updatePolicyMeta(mut: (com.example.kotlinview.data.local.datastore.PolicyMeta) -> com.example.kotlinview.data.local.datastore.PolicyMeta) =
        policy.update(mut)

    override fun findLocalImage(expId: String) = images.findAnyLocalImageFor(expId)

    override suspend fun prefetchLocalImages(items: List<ExperienceDtoMap>, maxToPersist: Int) {
        val toPersist = items.take(maxToPersist)
        toPersist.forEach { dto ->
            val id = dto.id ?: return@forEach
            val firstUrl = dto.images?.firstOrNull() ?: return@forEach
            images.saveOriginalIfNeeded(id, firstUrl)
        }
        images.enforceCountCap(maxCount = 12)
    }

    private fun bucketKeyFor(lat: Double, lng: Double): String =
        "${"%.2f".format(lat)}_${"%.2f".format(lng)}"

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
