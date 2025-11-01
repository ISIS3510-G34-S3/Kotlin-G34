package com.example.kotlinview.data.local.api

import android.content.Context
import com.example.kotlinview.data.local.ExperienceLocalStore
import com.example.kotlinview.data.local.ExperienceLocalStoreImpl
import com.example.kotlinview.data.map.ExperienceDtoMap
import java.io.File

class ExperienceLocalApiImpl(
    context: Context
) : ExperienceLocalApi {

    private val store: ExperienceLocalStore =
        ExperienceLocalStoreImpl(context.applicationContext)

    // -------- Room (SQLite SSOT) --------
    override suspend fun roomGetAll(): List<ExperienceDtoMap> =
        store.getAll()

    override suspend fun roomGetByIds(ids: List<String>): List<ExperienceDtoMap> =
        store.getByIds(ids)

    override suspend fun roomUpsertFromRemote(
        items: List<ExperienceDtoMap>,
        maxToPersist: Int,
        prefetchImages: Boolean
    ) {
        store.upsertFromRemote(
            items = items,
            maxToPersist = maxToPersist,
            currentLat = null,     // catalogue doesn't need nearest/bucket logic
            currentLng = null,
            prefetchImages = prefetchImages
        )
    }

    // -------- Local files (original images) --------
    override fun findLocalImage(expId: String): File? =
        store.findLocalImage(expId)

    override suspend fun prefetchLocalImages(items: List<ExperienceDtoMap>, maxToPersist: Int) =
        store.prefetchLocalImages(items, maxToPersist)

    override fun resolveImageSource(dto: ExperienceDtoMap): Pair<Boolean, String> {
        val id = dto.id.orEmpty()
        val f = store.findLocalImage(id)
        return if (f != null && f.exists()) {
            true to f.absolutePath     // caller can use "file://$path"
        } else {
            false to (dto.images.firstOrNull() ?: "")
        }
    }
}
