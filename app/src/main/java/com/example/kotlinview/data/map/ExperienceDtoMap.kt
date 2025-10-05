package com.example.kotlinview.data.map

data class ExperienceDtoMap(
    val id: String,

    // Basic
    val title: String? = null,
    val department: String? = null,
    val avgRating: Double? = null,
    val reviewsCount: Int? = null,

    // Host
    val hostVerified: Boolean? = null,
    val hostId: String? = null,
    val hostName: String? = null,   // filled from users/{hostId}

    // Location
    val latitude: Double? = null,
    val longitude: Double? = null,

    // Skills
    val skillsToLearn: List<String> = emptyList(),
    val skillsToTeach: List<String> = emptyList()
) {
    val hasLocation: Boolean get() = latitude != null && longitude != null
    val latLng: Pair<Double, Double>? get() =
        if (latitude != null && longitude != null) latitude to longitude else null
}
