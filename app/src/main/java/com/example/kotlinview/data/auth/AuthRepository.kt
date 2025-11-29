package com.example.kotlinview.data.auth

import com.google.firebase.auth.FirebaseUser

sealed interface AuthResult {
    data object Loading : AuthResult
    data class Success(val user: FirebaseUser) : AuthResult
    data class Error(val throwable: Throwable) : AuthResult
}

class AuthRepository(
    private val remote: AuthRemoteDataSource

) {
    fun currentUser(): FirebaseUser? = remote.currentUser()

    suspend fun signIn(email: String, password: String): AuthResult =
        runCatching { remote.signIn(email, password) }
            .fold(
                onSuccess = { AuthResult.Success(it) },
                onFailure = { AuthResult.Error(it) }
            )

    fun signOut() = remote.signOut()

    suspend fun signUp(email: String, password: String): AuthResult =
        runCatching { remote.signUp(email, password) }
            .fold(
                onSuccess = { AuthResult.Success(it) },
                onFailure = { AuthResult.Error(it) }
            )
}
