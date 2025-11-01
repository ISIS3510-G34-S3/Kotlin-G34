package com.example.kotlinview.data.user

import com.example.kotlinview.model.User

interface UserRepository {
    suspend fun getByEmail(email: String): User?
    suspend fun setLastSignInNow(email: String)
}