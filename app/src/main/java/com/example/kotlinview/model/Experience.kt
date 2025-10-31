package com.example.kotlinview.model

data class Experience(
    val id: String,
    val title: String,
    val hostName: String,
    val location: String,
    val verified: Boolean,
    val rating: Double,
    val reviewCount: Int,
    val description: String,
    val duration: String,
    val activityType: String,
    val skills: List<String>,
    val teachingSkills: List<String>,
    val learningSkills: List<String>,
    val accessibility: Boolean,
    val imageUrl: String = ""
)