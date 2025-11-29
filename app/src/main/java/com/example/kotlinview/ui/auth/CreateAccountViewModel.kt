package com.example.kotlinview.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.data.auth.AuthRepository
import com.example.kotlinview.data.auth.AuthResult
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CreateAccountUiState(
    val isLoading: Boolean = false,
    val success: Boolean = false,
    val errorMessage: String? = null
)

class CreateAccountViewModel(
    private val authRepo: AuthRepository = ServiceLocator.provideAuthRepository()
) : ViewModel() {

    private val _state = MutableStateFlow(CreateAccountUiState())
    val state: StateFlow<CreateAccountUiState> = _state

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    fun createAccount(name: String, email: String, password: String) {
        viewModelScope.launch {
            _state.value = CreateAccountUiState(isLoading = true)

            val signUp = authRepo.signUp(email, password)
            if (signUp is AuthResult.Error) {
                _state.value = CreateAccountUiState(
                    isLoading = false,
                    success = false,
                    errorMessage = signUp.throwable.message ?: "Failed to create account"
                )
                return@launch
            }

            val user = (signUp as AuthResult.Success).user
            val uid = user.uid
            val db = ServiceLocator.provideFirestore()

            val doc = hashMapOf<String, Any>(
                "uid" to uid,
                "email" to email,
                "name" to name,
                "displayName" to name,
                "about" to "",
                "languages" to emptyList<String>(),
                "avgHostRating" to 0.0,
                "photoURL" to "",
                "provider" to "password",
                "userType" to "",
                "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                "lastSignInAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
            )

            runCatching {
                db.collection("users").document(email)
                    .set(doc, com.google.firebase.firestore.SetOptions.merge())
                    .await()
            }.onFailure { t ->
                _state.value = CreateAccountUiState(
                    isLoading = false,
                    success = false,
                    errorMessage = t.message ?: "Account created, but failed saving profile"
                )
                return@launch
            }

            // IMPORTANT: Firebase has signed the user in automatically -> sign them out
            authRepo.signOut()

            _state.value = CreateAccountUiState(isLoading = false, success = true)
        }
    }
}

@Suppress("UNCHECKED_CAST")
class CreateAccountVmFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CreateAccountViewModel() as T
    }
}
