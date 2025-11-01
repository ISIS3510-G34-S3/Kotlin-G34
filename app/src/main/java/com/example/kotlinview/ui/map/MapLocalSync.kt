package com.example.kotlinview.ui.map

import com.example.kotlinview.data.local.ExperienceLocalStore
import com.example.kotlinview.data.map.ExperienceDtoMap

/**
 * Encapsulates how Map persists remote results into the local layers.
 * - Room upsert (SSOT)
 * - Nearest-bucket update (inside localStore.upsertFromRemote)
 * - No image prefetch (Map doesn't need images offline)
 */
class MapLocalSync(
    private val localStore: ExperienceLocalStore,
    private val maxToPersist: Int = 7
) {

    /**
     * Persist the latest remote results without blocking UI.
     * The ExperienceLocalStore implementation is responsible for:
     * - writing to Room
     * - maintaining nearest-bucket (Realm) for the given lat/lng
     * - skipping image prefetch for Map usage
     */
    suspend fun persistFromRemote(
        items: List<ExperienceDtoMap>,
        currentLat: Double,
        currentLng: Double
    ) {
        localStore.upsertFromRemote(
            items = items,
            maxToPersist = maxToPersist,
            currentLat = currentLat,
            currentLng = currentLng,
            prefetchImages = false // Map doesn't need local images
        )
    }
}
