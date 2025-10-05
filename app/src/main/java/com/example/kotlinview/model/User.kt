package com.example.kotlinview.model

data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val interestedCategories: List<String> = emptyList(), // para futuro matching
    val verified: Boolean = false,
)