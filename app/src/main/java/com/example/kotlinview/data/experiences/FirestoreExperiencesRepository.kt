package com.example.kotlinview.data.experiences

import com.example.kotlinview.data.map.ExperienceDtoMap
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentReference
import kotlinx.coroutines.tasks.await
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

// Helpers

private fun DocumentSnapshot.hostRef(): DocumentReference? =
    this.getDocumentReference("hostId")

private fun DocumentSnapshot.readHostEmail(): String {
    val v = this.get("hostId")
    val raw = when (v) {
        is DocumentReference -> v.id
        is String -> v
        else -> v?.toString() ?: ""
    }
    return raw.substringAfterLast('/').trim().lowercase()
}

private fun DocumentSnapshot.toExperienceDtoMap(): ExperienceDtoMap {
    val geo: GeoPoint? = this.getGeoPoint("location")

    val latValue: Double =
        geo?.latitude
            ?: this.getDouble("latitude")
            ?: this.getDouble("lat")
            ?: (this.get("lat") as? Number)?.toDouble()
            ?: 0.0

    val lngValue: Double =
        geo?.longitude
            ?: this.getDouble("longitude")
            ?: this.getDouble("lng")
            ?: (this.get("lng") as? Number)?.toDouble()
            ?: 0.0

    val avgRatingValue: Double =
        this.getDouble("avgRating")
            ?: this.getDouble("rating")
            ?: (this.get("avgRating") as? Number)?.toDouble()
            ?: (this.get("rating") as? Number)?.toDouble()
            ?: 0.0

    val reviewsCountValue: Int =
        (this.getLong("reviewsCount") ?: this.getLong("reviews"))?.toInt()
            ?: (this.get("reviewsCount") as? Number)?.toInt()
            ?: (this.get("reviews") as? Number)?.toInt()
            ?: 0

    val hostVerifiedValue: Boolean =
        this.getBoolean("hostVerified") ?: this.getBoolean("verified") ?: false


    val hostEmail = readHostEmail()

    val hostNameValue: String = this.getString("hostName").orEmpty()

    val toLearn: List<String> =
        (this.get("skillsToLearn") as? List<*>)?.filterIsInstance<String>()
            ?: (this.get("learnSkills") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()

    val toTeach: List<String> =
        (this.get("skillsToTeach") as? List<*>)?.filterIsInstance<String>()
            ?: (this.get("teachSkills") as? List<*>)?.filterIsInstance<String>()
            ?: emptyList()

    val imagesValue: List<String> =
        (this.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList()

    val priceCopValue: Long =
        this.getLong("priceCOP")
            ?: (this.get("priceCOP") as? Number)?.toLong()
            ?: 0L

    val durationValue: Int =
        this.getLong("duration")?.toInt()
            ?: (this.get("duration") as? Number)?.toInt()
            ?: 0

    return ExperienceDtoMap(
        id = this.id,
        title = this.getString("title").orEmpty(),
        department = this.getString("department").orEmpty(),
        avgRating = avgRatingValue,
        reviewsCount = reviewsCountValue,
        hostVerified = hostVerifiedValue,
        hostId = hostEmail,
        hostName = hostNameValue,
        latitude = latValue,
        longitude = lngValue,
        skillsToLearn = toLearn,
        skillsToTeach = toTeach,
        images = imagesValue,
        priceCOP = priceCopValue,
        duration = durationValue
    )
}

class FirestoreExperiencesRepository(
    private val db: FirebaseFirestore
) : ExperiencesRepository {

    // cache por id (String) del user doc
    private val hostNameCache = mutableMapOf<String, String?>()

    override suspend fun getNearest(lat: Double, lng: Double, topK: Int): List<ExperienceDtoMap> {
        val snap = db.collection("experiences")
            .whereEqualTo("isActive", true)
            .get()
            .await()

        val items = snap.documents.mapNotNull { doc ->
            val id = doc.id

            val geo = doc.getGeoPoint("location")
            val latitude = geo?.latitude
                ?: doc.getDouble("latitude")
                ?: doc.getDouble("lat")
                ?: 0.0
            val longitude = geo?.longitude
                ?: doc.getDouble("longitude")
                ?: doc.getDouble("lng")
                ?: 0.0

            val hostEmail = doc.readHostEmail()
            val hostRef = doc.get("hostId") as? DocumentReference
            val hostName = hostRef?.let { fetchHostName(it) }.orEmpty()

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
                title = doc.getString("title").orEmpty(),
                department = doc.getString("department").orEmpty(),
                avgRating = doc.getDouble("avgRating") ?: doc.getDouble("rating") ?: 0.0,
                reviewsCount = (doc.getLong("reviewsCount") ?: doc.getLong("reviews") ?: 0L).toInt(),
                hostVerified = (doc.getBoolean("hostVerified") ?: doc.getBoolean("verified") ?: false),
                hostId = hostEmail,
                hostName = hostName,
                latitude = latitude,
                longitude = longitude,
                skillsToLearn = skillsToLearn,
                skillsToTeach = skillsToTeach,
                images = (doc.get("images") as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
                priceCOP = doc.getLong("priceCOP") ?: 0L,
                duration = (doc.getLong("duration") ?: 0L).toInt()
            )
        }

        return items
            .sortedBy { dto -> haversineKm(lat, lng, dto.latitude, dto.longitude) }
            .take(topK)
    }

    // ********** CAMBIO: buscar nombre tomando DocumentReference **********
    private suspend fun fetchHostName(hostRef: DocumentReference): String? {
        val key = hostRef.id
        if (hostNameCache.containsKey(key)) return hostNameCache[key]
        val doc = hostRef.get().await()
        val name = doc.getString("name") ?: doc.getString("displayName")
        hostNameCache[key] = name
        return name
    }
    // ********************************************************************

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
        var query: Query = db.collection("experiences")
        if (limit != null) query = query.limit(limit.toLong())

        val snap = query.get().await()
        return snap.documents.mapNotNull { doc ->
            runCatching { doc.toExperienceDtoMap() }.getOrNull()
        }
    }

    override suspend fun getRandomFeed(
        limit: Int,
        excludeHostIds: Set<String>,   // emails (lowercase)
        onlyActive: Boolean
    ): List<ExperienceDtoMap> {

        val poolSize = maxOf(limit * 5, 100)

        // Normaliza el email a excluir (1 usuario)
        val excludeEmail = excludeHostIds.firstOrNull()?.trim()?.lowercase().orEmpty()

        var q: Query = db.collection("experiences")
        if (onlyActive) q = q.whereEqualTo("isActive", true)

        // Filtrado en servidor: not-in soporta hasta 10 valores → aquí solo 1
        if (excludeEmail.isNotEmpty()) {
            val userRef: DocumentReference = db.collection("users").document(excludeEmail)
            q = q.whereNotIn("hostId", listOf(userRef))
        }

        q = q.limit(poolSize.toLong())

        // Ejecuta
        val snap = q.get().await()

        // Mapea
        val all = snap.documents.mapNotNull { doc ->
            runCatching { doc.toExperienceDtoMap() }.getOrNull()
        }

        // Filtro en cliente (respaldo, por si quedan legacy en String o algo raro)
        val filtered = if (excludeEmail.isEmpty()) {
            all
        } else {
            all.filter { exp ->
                val hostEmail = exp.hostId.trim().lowercase() // mapper ya lo deja como email
                hostEmail.isNotEmpty() && hostEmail != excludeEmail
            }
        }

        return filtered.shuffled().take(limit)
    }
}
