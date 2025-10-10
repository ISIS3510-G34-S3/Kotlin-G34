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
import com.google.firebase.Timestamp
import java.util.Date
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope


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

private fun Long.toTs(): Timestamp = Timestamp(Date(this))

// overlap if (startA < endB) && (endA > startB)
private fun overlap(aStart: Long, aEnd: Long, bStart: Long, bEnd: Long): Boolean =
    aStart < bEnd && aEnd > bStart


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

private suspend fun isAvailableByBookings(
    db: FirebaseFirestore,
    expId: String,
    startAtMs: Long,
    endAtMs: Long
): Boolean {
    // 1 sola desigualdad en servidor (startsAt < endAt)
    val q = db.collection("experiences").document(expId).collection("bookings")
        .whereLessThan("startsAt", Timestamp(Date(endAtMs)))
        // .whereEqualTo("status", "active") // si lo usas
        .limit(25) // margen razonable; luego filtramos en cliente

    val snap = q.get().await()
    if (snap.isEmpty) return true

    // Filtro de solapamiento en cliente (endsAt > startAt)
    val anyOverlap = snap.documents.any { b ->
        val s = (b.get("startsAt") as? Timestamp)?.toDate()?.time
        val e = (b.get("endsAt") as? Timestamp)?.toDate()?.time
        s != null && e != null && (s < endAtMs && e > startAtMs)
    }
    return !anyOverlap
}


class FirestoreExperiencesRepository(
    private val db: FirebaseFirestore
) : ExperiencesRepository {

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


    private suspend fun fetchHostName(hostRef: DocumentReference): String? {
        val key = hostRef.id
        if (hostNameCache.containsKey(key)) return hostNameCache[key]
        val doc = hostRef.get().await()
        val name = doc.getString("name") ?: doc.getString("displayName")
        hostNameCache[key] = name
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

    override suspend fun getFilteredFeed(
        limit: Int,
        excludeHostEmails: Set<String>,
        department: String?,
        startAtMs: Long?,
        endAtMs: Long?,
        onlyActive: Boolean
    ): List<ExperienceDtoMap> {

        // 1) Query base en servidor (department + isActive)
        var q: Query = db.collection("experiences")
        if (onlyActive) q = q.whereEqualTo("isActive", true)
        if (!department.isNullOrBlank()) q = q.whereEqualTo("department", department.trim())

        // Trae un pool generoso, luego filtramos por disponibilidad y autor
        val poolSize = maxOf(limit * 5, 150)
        q = q.limit(poolSize.toLong())

        val snap = q.get().await()

        // 2) Mapear a DTOs (y normalizar hostId como email para poder excluir)
        val all = snap.documents.mapNotNull { doc ->
            runCatching {
                val dto = doc.toExperienceDtoMap()
                dto.copy(hostId = doc.readHostEmail()) // garantiza email en hostId
            }.getOrNull()
        }

        // 3) Excluir experiencias del usuario logueado (por email)
        val exclude = excludeHostEmails.map { it.trim().lowercase() }.toSet()
        val notMine = if (exclude.isEmpty()) all else all.filter { it.hostId !in exclude }

        // 4) Filtro de disponibilidad (si hay rango)
        val filtered = if (startAtMs != null && endAtMs != null && endAtMs > startAtMs) {
            coroutineScope {
                notMine.map { dto ->
                    async {
                        val ok = isAvailableByBookings(db, dto.id, startAtMs, endAtMs)
                        if (ok) dto else null
                    }
                }.awaitAll().filterNotNull()
            }
        } else {
            notMine
        }

        return filtered.shuffled().take(limit)
    }

}
