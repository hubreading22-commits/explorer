package com.collegefiles.app.ui.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.smbcore.SmbClient
import com.smbcore.model.FileItem
import com.smbcore.model.SmbResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream

class FileOpsViewModel(
    private val smbClient: SmbClient = AppModule.smbClient
) : ViewModel() {

    private val _state = MutableStateFlow(FileOpsState())
    val state: StateFlow<FileOpsState> = _state.asStateFlow()

    private var clipboardItem: ClipboardItem? = null
    val hasClipboardItem: Boolean get() = clipboardItem != null

    // ─── Action Sheet ────────────────────────────────────────────────────────────

    fun onLongPress(item: FileItem) {
        _state.update { it.copy(targetItem = item, showActionSheet = true) }
    }

    fun dismissActionSheet() {
        _state.update { it.copy(showActionSheet = false) }
    }

    // ─── Dialog Triggers ─────────────────────────────────────────────────────────

    fun requestRename() {
        _state.update { it.copy(showActionSheet = false, showRenameDialog = true) }
    }

    fun requestDelete() {
        _state.update { it.copy(showActionSheet = false, showDeleteDialog = true) }
    }

    fun requestCreateFolder() {
        _state.update { it.copy(showCreateFolderDialog = true) }
    }

    fun dismissAllDialogs() {
        _state.update {
            it.copy(
                showRenameDialog = false,
                showDeleteDialog = false,
                showCreateFolderDialog = false,
                showActionSheet = false
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, error = null) }
    }

    // ─── Operations ──────────────────────────────────────────────────────────────

    fun rename(shareName: String, newName: String, onSuccess: () -> Unit) {
        val item = _state.value.targetItem ?: return
        _state.update { it.copy(isLoading = true, showRenameDialog = false) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                smbClient.rename(shareName, item.path, newName)
            }
            when (result) {
                is SmbResult.Success -> {
                    _state.update { it.copy(isLoading = false, successMessage = "Renamed successfully") }
                    onSuccess()
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Rename failed. Check permissions.") }
                }
            }
        }
    }

    fun delete(shareName: String, onSuccess: () -> Unit) {
        val item = _state.value.targetItem ?: return
        _state.update { it.copy(isLoading = true, showDeleteDialog = false) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                smbClient.delete(shareName, item.path)
            }
            when (result) {
                is SmbResult.Success -> {
                    _state.update { it.copy(isLoading = false, successMessage = "Deleted successfully") }
                    onSuccess()
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Delete failed. Check permissions.") }
                }
            }
        }
    }

    fun createFolder(shareName: String, currentPath: String, folderName: String, onSuccess: () -> Unit) {
        if (folderName.isBlank()) return
        val fullPath = if (currentPath.isEmpty()) folderName else "$currentPath\\$folderName"
        _state.update { it.copy(isLoading = true, showCreateFolderDialog = false) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                smbClient.createFolder(shareName, fullPath)
            }
            when (result) {
                is SmbResult.Success -> {
                    _state.update { it.copy(isLoading = false, successMessage = "Folder created") }
                    onSuccess()
                }
                is SmbResult.Failure -> {
                    val errorMsg = if (result.error is com.smbcore.model.SmbError.AlreadyExists) {
                        "Folder already exists."
                    } else {
                        "Failed to create folder."
                    }
                    _state.update { it.copy(isLoading = false, error = errorMsg) }
                }
            }
        }
    }

    fun upload(shareName: String, remotePath: String, inputStream: InputStream, fileName: String, onSuccess: () -> Unit) {
        _state.update { it.copy(isLoading = true) }

        viewModelScope.launch {
            val fullRemotePath = if (remotePath.isEmpty()) fileName else "$remotePath\\$fileName"
            val result = withContext(Dispatchers.IO) {
                smbClient.upload(inputStream, shareName, fullRemotePath)
            }
            when (result) {
                is SmbResult.Success -> {
                    _state.update { it.copy(isLoading = false, successMessage = "Upload complete") }
                    onSuccess()
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Upload failed. Check connection.") }
                }
            }
        }
    }

    // ─── Clipboard ───────────────────────────────────────────────────────────────

    fun copyItem(shareName: String) {
        val item = _state.value.targetItem ?: return
        clipboardItem = ClipboardItem(item, shareName, isCut = false)
        _state.update { it.copy(showActionSheet = false, successMessage = "Copied to clipboard") }
    }

    fun cutItem(shareName: String) {
        val item = _state.value.targetItem ?: return
        clipboardItem = ClipboardItem(item, shareName, isCut = true)
        _state.update { it.copy(showActionSheet = false, successMessage = "Cut to clipboard") }
    }

    fun paste(currentShare: String, currentPath: String, onSuccess: () -> Unit) {
        val clip = clipboardItem ?: return
        _state.update { it.copy(isLoading = true) }

        val destPath = if (currentPath.isEmpty()) clip.item.name else "$currentPath\\${clip.item.name}"

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (clip.isCut) {
                    smbClient.move(clip.sourceShare, clip.item.path, currentShare, destPath)
                } else {
                    smbClient.copy(clip.sourceShare, clip.item.path, currentShare, destPath)
                }
            }
            when (result) {
                is SmbResult.Success -> {
                    if (clip.isCut) clipboardItem = null // Clear after move
                    _state.update { it.copy(isLoading = false, successMessage = "Paste complete") }
                    onSuccess()
                }
                is SmbResult.Failure -> {
                    _state.update { it.copy(isLoading = false, error = "Paste failed. Check permissions.") }
                }
            }
        }
    }
}

data class ClipboardItem(
    val item: FileItem,
    val sourceShare: String,
    val isCut: Boolean
)
