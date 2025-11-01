package com.example.kotlinview.data.experiences

import com.example.kotlinview.data.map.ExperienceDtoMap

interface ExperiencesRepository {

    /**
     * Returns up to [topK] nearby experiences sorted by distance from [lat],[lng].
     * Distance is computed client-side (no geohash in DB).
     * Only active experiences with valid coordinates are returned.
     */
    suspend fun getNearest(
        lat: Double,
        lng: Double,
        topK: Int = 5
    ): List<ExperienceDtoMap>

    suspend fun getExperiences(
        limit: Int? = null
    ): List<ExperienceDtoMap>

    suspend fun getRandomFeed(
        limit: Int,
        excludeHostIds: Set<String> = emptySet(),
        onlyActive: Boolean = true
    ): List<ExperienceDtoMap>

    suspend fun getFilteredFeed(
        limit: Int,
        excludeHostEmails: Set<String>,
        department: String?,
        startAtMs: Long?,
        endAtMs: Long?,
        onlyActive: Boolean = true
    ): List<ExperienceDtoMap>

    /**
     * Returns the average rating for reviews in the subcollection
     * experiences/{experienceId}/reviews during [sinceMs, now].
     * If there are no reviews in that window, returns null.
     */
    suspend fun getRecentAverageRating(
        experienceId: String,
        sinceMs: Long
    ): Double?
}
