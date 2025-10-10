package com.example.kotlinview.data.map

data class ExperienceDtoMap(
    val id: String,
    val title: String,
    val department: String,
    val avgRating: Double,
    val reviewsCount: Int,
    val hostVerified: Boolean,
    val hostId: String,
    val hostName: String,
    val latitude: Double,
    val longitude: Double,
    val skillsToLearn: List<String>,
    val skillsToTeach: List<String>,
    val images: List<String>,
    val priceCOP: Long,
    val duration: Int
)