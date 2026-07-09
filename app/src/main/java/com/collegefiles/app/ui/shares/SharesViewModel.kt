package com.collegefiles.app.ui.shares

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.SmbClient
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SharesViewModel(
    private val smbClient: SmbClient = AppModule.smbClient
) : ViewModel() {

    private val _state = MutableStateFlow(SharesState())
    val state: StateFlow<SharesState> = _state.asStateFlow()

    init {
        loadShares()
    }

    fun refresh() {
        loadShares()
    }

    private fun loadShares() {
        if (!smbClient.isConnected()) {
            _state.update { it.copy(sessionExpired = true, isConnected = false) }
            return
        }

        _state.update { it.copy(isLoading = true, error = null, isConnected = true) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                smbClient.listShares()
            }

            when (result) {
                is SmbResult.Success -> {
                    // Filter out hidden/administrative shares if needed (typically ending with $)
                    val visibleShares = result.data.filter { !it.name.endsWith("$") }
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            shares = visibleShares,
                            error = null
                        ) 
                    }
                }
                is SmbResult.Failure -> {
                    when (result.error) {
                        is SmbError.NetworkUnavailable, is SmbError.AuthenticationFailed -> {
                            // If network drops or auth fails, we assume session is dead
                            _state.update { it.copy(sessionExpired = true, isConnected = false) }
                        }
                        else -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "Unable to load your files. Please check your connection."
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
