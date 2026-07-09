package com.collegefiles.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.SmbClient
import com.smbcore.model.Credentials
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LoginViewModel(
    private val smbClient: SmbClient = AppModule.smbClient
) : ViewModel() {

    private val _state = MutableStateFlow(LoginState())
    val state: StateFlow<LoginState> = _state.asStateFlow()

    private val hardcodedDomain = "digihub"

    fun onUsernameChange(username: String) {
        _state.update { it.copy(username = username, error = null) }
    }

    fun onPasswordChange(password: String) {
        _state.update { it.copy(password = password, error = null) }
    }

    fun login(onSuccess: () -> Unit) {
        val currentState = _state.value
        if (currentState.username.isBlank() || currentState.password.isBlank()) {
            _state.update { it.copy(error = "Username and Password are required") }
            return
        }

        _state.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            val credentials = Credentials(
                username = currentState.username,
                password = currentState.password.toCharArray(),
                domain = hardcodedDomain
            )

            val result = withContext(Dispatchers.IO) {
                smbClient.login(credentials)
            }

            when (result) {
                is SmbResult.Success -> {
                    // Clear password on success for security
                    _state.update { it.copy(password = "", isLoading = false) }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                }
                is SmbResult.Failure -> {
                    val errorMsg = when (result.error) {
                        is SmbError.AuthenticationFailed -> "Invalid username or password"
                        is SmbError.PermissionDenied -> "Permission Denied"
                        is SmbError.NetworkUnavailable -> "Network Unavailable. Check connection."
                        else -> "Login failed. Try again."
                    }
                    // Keep username, clear password on failure
                    _state.update { it.copy(password = "", isLoading = false, error = "❌ $errorMsg") }
                }
            }
        }
    }
}
