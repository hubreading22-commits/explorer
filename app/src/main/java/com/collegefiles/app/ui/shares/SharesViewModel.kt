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



    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
    }

    fun refresh() {
        loadShares()
    }

    private fun loadShares() {
        _state.update { it.copy(isLoading = true, error = null, isConnected = true) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                smbClient.listShares()
            }

            when (result) {
                is SmbResult.Success -> {
                    // Filter out hidden/administrative shares if needed (typically ending with $)
                    val visibleShares = result.data
                        .filter { !it.name.endsWith("$") }
                        .sortedBy { it.name.lowercase() }
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
                        is SmbError.AuthenticationFailed -> {
                            _state.update { it.copy(sessionExpired = true, isConnected = false) }
                        }
                        is SmbError.NetworkUnavailable -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    isConnected = false,
                                    error = "Network unavailable. Please check your connection."
                                )
                            }
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
