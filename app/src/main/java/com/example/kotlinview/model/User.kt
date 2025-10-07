package com.example.kotlinview.model

import com.google.firebase.Timestamp

data class User(
    val email: String = "",
    val displayName: String = "",
    val photoURL: String = "",
    val provider: String = "",
    val userType: String = "",
    val createdAt: Timestamp? = null,
    val lastSignInAt: Timestamp? = null
)