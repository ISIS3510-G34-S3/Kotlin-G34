package com.example.kotlinview.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kotlinview.core.ServiceLocator
import com.example.kotlinview.core.SessionManager
import com.example.kotlinview.data.auth.AuthRepository
import com.example.kotlinview.data.auth.AuthResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AuthViewModel(
    private val authRepo: AuthRepository = ServiceLocator.provideAuthRepository()
) : ViewModel() {

    private val _state = MutableStateFlow<AuthResult?>(null)
    val state: StateFlow<AuthResult?> = _state

    fun tryAutoLoginAndLoadProfile() {
        viewModelScope.launch {
            val current = authRepo.currentUser()
            if (current != null) {
                _state.value = AuthResult.Loading
                ServiceLocator.preloadUserProfileIfLogged()
                _state.value = AuthResult.Success(current)
            }
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            _state.value = AuthResult.Loading
            val result = authRepo.signIn(email, password)
            _state.value = result
            if (result is AuthResult.Success) {
                ServiceLocator.preloadUserProfileIfLogged()
            }
        }
    }
}

@Suppress("UNCHECKED_CAST")
class AuthVmFactory : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AuthViewModel() as T
    }
}
