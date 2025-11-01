package com.example.kotlinview.data.local.api

import com.example.kotlinview.data.map.ExperienceDtoMap
import java.io.File

/**
 * Black-box local storage API for the Experience Catalogue.
 * - No internal threading; all are suspend so the caller controls concurrency.
 * - No networking; purely local (Room + Files).
 */
interface ExperienceLocalApi {

    // -------- Room (SQLite SSOT) --------
    suspend fun roomGetAll(): List<ExperienceDtoMap>
    suspend fun roomGetByIds(ids: List<String>): List<ExperienceDtoMap>
    suspend fun roomUpsertFromRemote(
        items: List<ExperienceDtoMap>,
        maxToPersist: Int,
        prefetchImages: Boolean = true
    )

    // -------- Local files (original images) --------
    /** Returns the file if an original image for this experience was saved locally, else null. */
    fun findLocalImage(expId: String): File?

    /**
     * Saves original images for up to [maxToPersist] experiences to local files.
     * (Generally called right after roomUpsertFromRemote when online.)
     */
    suspend fun prefetchLocalImages(items: List<ExperienceDtoMap>, maxToPersist: Int)

    /**
     * Prefer local file image if present; otherwise return first remote image URL (or empty).
     * Returns (isLocalFile, pathOrUrl). If isLocalFile=true, pathOrUrl is an absolute file path.
     */
    fun resolveImageSource(dto: ExperienceDtoMap): Pair<Boolean, String>
}
