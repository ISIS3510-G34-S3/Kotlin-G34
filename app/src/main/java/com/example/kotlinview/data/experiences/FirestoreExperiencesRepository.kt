package com.example.kotlinview.data.experiences

import com.example.kotlinview.data.map.ExperienceDtoMap
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.*

class FirestoreExperiencesRepository(
    private val db: FirebaseFirestore
) : ExperiencesRepository {

    // small in-memory cache to avoid reloading the same host repeatedly
    private val hostNameCache = mutableMapOf<String, String?>()

    override suspend fun getNearest(lat: Double, lng: Double, topK: Int): List<ExperienceDtoMap> {
        val snap = db.collection("experiences")
            .whereEqualTo("isActive", true)
            .get()
            .await()

        val items = snap.documents.mapNotNull { doc ->
            val id = doc.id

            // GeoPoint (preferred) or legacy lat/lng
            val geo = doc.getGeoPoint("location")
            val latitude = geo?.latitude ?: doc.getDouble("latitude")
            val longitude = geo?.longitude ?: doc.getDouble("longitude")
            if (latitude == null || longitude == null) return@mapNotNull null

            val hostId = doc.getString("hostId")
            val hostName = hostId?.let { fetchHostName(it) }

            val skillsToLearn: List<String> = when (val v = doc.get("skillsToLearn")) {
                is List<*> -> v.filterIsInstance<String>()
                else -> emptyList()
            }
            val skillsToTeach: List<String> = when (val v = doc.get("skillsToTeach")) {
                is List<*> -> v.filterIsInstance<String>()
                else -> emptyList()
            }

            ExperienceDtoMap(
                id = id,
                title = doc.getString("title"),
                department = doc.getString("department"),
                avgRating = doc.getDouble("avgRating"),
                reviewsCount = (doc.getLong("reviewsCount") ?: 0L).toInt(),
                hostVerified = doc.getBoolean("hostVerified"),
                hostId = hostId,
                hostName = hostName,
                latitude = latitude,
                longitude = longitude,
                skillsToLearn = skillsToLearn,
                skillsToTeach = skillsToTeach
            )
        }

        return items
            .sortedBy { dto -> haversineKm(lat, lng, dto.latitude ?: 0.0, dto.longitude ?: 0.0) }
            .take(topK)
    }

    private suspend fun fetchHostName(hostId: String): String? {
        if (hostNameCache.containsKey(hostId)) return hostNameCache[hostId]
        val doc = db.collection("users").document(hostId).get().await()
        val name = doc.getString("name") ?: doc.getString("displayName")
        hostNameCache[hostId] = name
        return name
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) *
                cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
