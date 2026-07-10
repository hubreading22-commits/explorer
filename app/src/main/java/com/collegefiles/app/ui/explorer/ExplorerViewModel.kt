package com.collegefiles.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.SmbClient
import com.smbcore.model.FileItem
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExplorerViewModel(
    private val shareName: String,
    private val smbClient: SmbClient = AppModule.smbClient
) : ViewModel() {

    private val _state = MutableStateFlow(ExplorerState(currentShare = shareName))
    val state: StateFlow<ExplorerState> = _state.asStateFlow()
    
    private var isDownloadCancelled = false

    init {
        loadDirectory()
    }

    fun refresh() {
        loadDirectory()
    }

    fun onFolderClick(folder: FileItem) {
        val newBreadcrumbs = _state.value.breadcrumbs.toMutableList()
        newBreadcrumbs.add(folder.name)
        _state.update { it.copy(breadcrumbs = newBreadcrumbs) }
        loadDirectory()
    }

    fun onFileClick(file: FileItem) {
        // Will be implemented in Phase 6 (Viewers)
        _state.update { it.copy(selectedFile = file) }
    }

    fun onLongClick(item: FileItem) {
        // Future Phase: show Action Bottom Sheet or Menu
    }

    fun onBreadcrumbClick(index: Int) {
        // Drop all breadcrumbs after the clicked index
        val newBreadcrumbs = _state.value.breadcrumbs.take(index + 1)
        _state.update { it.copy(breadcrumbs = newBreadcrumbs) }
        loadDirectory()
    }

    fun onHomeClick() {
        _state.update { it.copy(breadcrumbs = emptyList()) }
        loadDirectory()
    }
    
    fun openDocument(file: FileItem) {
        if (_state.value.isDownloading) return
        
        _state.update { it.copy(
            isDownloading = true,
            downloadingFileName = file.name,
            downloadProgress = 0f
        ) }
        
        isDownloadCancelled = false
        val path = _state.value.breadcrumbs.joinToString("\\")
        
        viewModelScope.launch {
            val result = AppModule.documentSessionService.openDocument(
                shareName = shareName,
                path = path,
                fileItem = file,
                onProgress = { progress ->
                    _state.update { it.copy(downloadProgress = progress) }
                },
                cancelSignal = { isDownloadCancelled }
            )
            
            _state.update { it.copy(
                isDownloading = false,
                downloadingFileName = null,
                downloadProgress = null
            ) }
            
            // Note: Error handling toast can be done via SideEffect or Event flow in production,
            // but for simplicity, we let the UI observe state or keep it clean here.
        }
    }
    
    fun cancelDownload() {
        isDownloadCancelled = true
        _state.update { it.copy(isDownloading = false, downloadingFileName = null, downloadProgress = null) }
    }

    /**
     * Called when the Android back button is pressed.
     * Returns true if we navigated up a folder.
     * Returns false if we are already at the root (signaling the UI to pop back to Shares Screen).
     */
    fun navigateUp(): Boolean {
        val currentBreadcrumbs = _state.value.breadcrumbs
        if (currentBreadcrumbs.isEmpty()) {
            return false // Already at root, let system handle back navigation
        }
        
        val newBreadcrumbs = currentBreadcrumbs.dropLast(1)
        _state.update { it.copy(breadcrumbs = newBreadcrumbs) }
        loadDirectory()
        return true
    }

    private fun loadDirectory() {
        _state.update { it.copy(isLoading = true, error = null, connectionState = ConnectionState.Connected) }

        viewModelScope.launch {
            val path = _state.value.breadcrumbs.joinToString("\\")
            
            val result = withContext(Dispatchers.IO) {
                smbClient.listDirectory(shareName, path)
            }

            when (result) {
                is SmbResult.Success -> {
                    // Sort: Folders first, then files by name
                    val sortedFiles = result.data.sortedWith(
                        compareByDescending<FileItem> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    
                    _state.update { 
                        it.copy(
                            isLoading = false,
                            files = sortedFiles,
                            error = null
                        ) 
                    }
                }
                is SmbResult.Failure -> {
                    when (result.error) {
                        is SmbError.AuthenticationFailed -> {
                            _state.update { it.copy(connectionState = ConnectionState.Expired) }
                        }
                        is SmbError.NetworkUnavailable -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    connectionState = ConnectionState.Disconnected,
                                    error = "Network unavailable. Please check your connection."
                                )
                            }
                        }
                        is SmbError.PermissionDenied -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "Permission Denied. You do not have access to this folder."
                                )
                            }
                        }
                        else -> {
                            _state.update { 
                                it.copy(
                                    isLoading = false,
                                    error = "Unable to load folder. Please check your connection."
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
