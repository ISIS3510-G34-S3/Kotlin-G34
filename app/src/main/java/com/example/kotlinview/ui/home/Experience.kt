package com.example.kotlinview.ui.home

import java.util.Date

data class Experience(
    val title: String,
    val rating: Double = 0.0,
    val department: String = "",
    val reviewCount: Int = 0,
    val duration: Int = 0,
    val learnSkills: List<String> = emptyList(),
    val teachSkills: List<String> = emptyList(),
    val hostVerified: Boolean = false,
    val hostName: String = "",
    val imageUrl: String = ""
)