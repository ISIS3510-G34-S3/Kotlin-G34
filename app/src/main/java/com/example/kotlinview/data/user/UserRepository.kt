package com.example.kotlinview.data.user

import com.example.kotlinview.model.User

interface UserRepository {
    suspend fun getUser(uid: String): User?
}