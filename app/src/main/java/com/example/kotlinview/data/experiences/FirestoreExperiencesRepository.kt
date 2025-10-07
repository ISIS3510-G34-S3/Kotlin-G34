package com.example.kotlinview.data.experiences

import com.example.kotlinview.data.map.ExperienceDtoMap
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import com.google.firebase.firestore.Query

private fun DocumentSnapshot.toExperienceDtoMap(): ExperienceDtoMap {
    // Ubicación: GeoPoint preferido
    val geo: GeoPoint? = getGeoPoint("location")

    val latValue: Double? =
        geo?.latitude
            ?: getDouble("latitude")
            ?: (get("lat") as? Number)?.toDouble()

    val lngValue: Double? =
        geo?.longitude
            ?: getDouble("longitude")
            ?: (get("lng") as? Number)?.toDouble()

    // Rating y reviews (acepta alias)
    val avgRatingValue: Double? =
        getDouble("avgRating")
            ?: getDouble("rating")
            ?: (get("avgRating") as? Number)?.toDouble()
            ?: (get("rating") as? Number)?.toDouble()

    val reviewsCountValue: Int =
        (getLong("reviewsCount") ?: getLong("reviews"))?.toInt()
            ?: (get("reviewsCount") as? Number)?.toInt()
            ?: (get("reviews") as? Number)?.toInt()
            ?: 0

    // Verificación (alias)
    val hostVerifiedValue: Boolean? =
        getBoolean("hostVerified")
            ?: (get("verified") as? Boolean)

    // Skills (alias)
    val toLearn: List<String> =
        (get("skillsToLearn") as? List<*>)?.filterIsInstance<String>()
            ?: (get("learnSkills") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()

    val toTeach: List<String> =
        (get("skillsToTeach") as? List<*>)?.filterIsInstance<String>()
            ?: (get("teachSkills") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()

    return ExperienceDtoMap(
        id = id,
        title = getString("title"),
        department = getString("department"),
        avgRating = avgRatingValue,
        reviewsCount = reviewsCountValue,
        hostVerified = hostVerifiedValue,
        hostId = getString("hostId"),
        hostName = getString("hostName"),
        latitude = latValue,
        longitude = lngValue,
        skillsToLearn = toLearn,
        skillsToTeach = toTeach
    )
}

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
            val latitude = geo?.latitude ?: doc.getDouble("latitude") ?: doc.getDouble("lat")
            val longitude = geo?.longitude ?: doc.getDouble("longitude") ?: doc.getDouble("lng")
            if (latitude == null || longitude == null) return@mapNotNull null

            val hostId = doc.getString("hostId")
            val hostName = hostId?.let { fetchHostName(it) }

            val skillsToLearn: List<String> = when (val v = doc.get("skillsToLearn") ?: doc.get("learnSkills")) {
                is List<*> -> v.filterIsInstance<String>()
                else -> emptyList()
            }
            val skillsToTeach: List<String> = when (val v = doc.get("skillsToTeach") ?: doc.get("teachSkills")) {
                is List<*> -> v.filterIsInstance<String>()
                else -> emptyList()
            }

            ExperienceDtoMap(
                id = id,
                title = doc.getString("title"),
                department = doc.getString("department"),
                avgRating = doc.getDouble("avgRating") ?: doc.getDouble("rating"),
                reviewsCount = (doc.getLong("reviewsCount") ?: doc.getLong("reviews") ?: 0L).toInt(),
                hostVerified = doc.getBoolean("hostVerified") ?: doc.getBoolean("verified"),
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

    override suspend fun getExperiences(limit: Int?): List<ExperienceDtoMap> {
        var query: Query = db.collection("experiences")         // <-- tipa como Query
        if (limit != null) query = query.limit(limit.toLong())

        val snap = query.get().await()
        return snap.documents.mapNotNull { doc ->
            try { doc.toExperienceDtoMap() } catch (_: Exception) { null }
        }
    }
}
