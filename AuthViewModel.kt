package com.pdfmakerstudio.app.ui.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.pdfmakerstudio.app.domain.model.AuthUser
import com.pdfmakerstudio.app.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AuthMode {
    LOGIN,
    REGISTER
}

data class AuthUiState(
    val mode: AuthMode = AuthMode.LOGIN,
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false
)

sealed interface AuthEvent {
    data class ShowMessage(val message: String) : AuthEvent
}

class AuthViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AuthEvent>()
    val events: SharedFlow<AuthEvent> = _events.asSharedFlow()

    val authState = authRepository.observeAuthState()

    fun setMode(mode: AuthMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun updateEmail(email: String) {
        _uiState.update { it.copy(email = email) }
    }

    fun updatePassword(password: String) {
        _uiState.update { it.copy(password = password) }
    }

    fun submitEmailAuth() {
        val state = _uiState.value
        val email = state.email.trim()
        val password = state.password

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emitMessage("Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            emitMessage("Password must be at least 6 characters.")
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result: Result<AuthUser> = when (state.mode) {
                AuthMode.LOGIN -> authRepository.signInWithEmail(email, password)
                AuthMode.REGISTER -> authRepository.registerWithEmail(email, password)
            }
            _uiState.update { it.copy(isLoading = false) }
            result.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Authentication failed.")
            }
        }
    }

    fun signInWithGoogleToken(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val result = authRepository.signInWithGoogleIdToken(idToken)
            _uiState.update { it.copy(isLoading = false) }
            result.onFailure { err ->
                emitMessage(err.localizedMessage ?: "Google Sign-In failed.")
            }
        }
    }

    fun onGoogleSignInError(message: String) {
        emitMessage(message)
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(AuthEvent.ShowMessage(message))
        }
    }

    class Factory(private val authRepository: AuthRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AuthViewModel(authRepository) as T
        }
    }
}
