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
import android.net.Uri
import com.smbcore.model.SmbError

class FileOpsViewModel(
    private val smbClient: SmbClient = AppModule.smbClient
) : ViewModel() {

    private val _state = MutableStateFlow(FileOpsState())
    val state: StateFlow<FileOpsState> = _state.asStateFlow()

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
                showActionSheet = false,
                showBatchDeleteDialog = false
            )
        }
    }

    fun clearMessages() {
        _state.update { it.copy(successMessage = null, error = null) }
    }

    // ─── Operations ──────────────────────────────────────────────────────────────

    object FilenameValidator {
        private val reservedNames = setOf(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
        )

        fun validate(name: String): String? {
            if (name.isBlank()) return "Name cannot be empty"
            if (name == "." || name == "..") return "Invalid name '.' or '..'"
            if (name.endsWith(" ") || name.endsWith(".")) return "Name cannot end with a space or dot"
            val baseName = name.substringBeforeLast('.').uppercase()
            if (reservedNames.contains(baseName)) return "Reserved device name"
            val invalidChars = listOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
            if (name.any { it in invalidChars }) return "Name contains invalid characters"
            return null
        }
    }

    fun rename(shareName: String, newName: String, onSuccess: () -> Unit) {
        val item = _state.value.targetItem ?: return
        
        val errorMsg = FilenameValidator.validate(newName)
        if (errorMsg != null) {
            _state.update { it.copy(error = errorMsg) }
            return
        }

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
                    val msg = when (result.error) {
                        is SmbError.AlreadyExists -> "A file with that name already exists."
                        is SmbError.PermissionDenied -> "Permission denied."
                        else -> "Rename failed."
                    }
                    _state.update { it.copy(isLoading = false, error = msg) }
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
        val errorMsg = FilenameValidator.validate(folderName)
        if (errorMsg != null) {
            _state.update { it.copy(error = errorMsg) }
            return
        }

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
                    val errorMsgStr = if (result.error is com.smbcore.model.SmbError.AlreadyExists) {
                        "Folder already exists."
                    } else {
                        "Failed to create folder."
                    }
                    _state.update { it.copy(isLoading = false, error = errorMsgStr) }
                }
            }
        }
    }

    fun upload(uri: Uri, shareName: String, remotePath: String, fileName: String, onSuccess: () -> Unit) {
        val fullRemotePath = if (remotePath.isEmpty()) fileName else "$remotePath\\$fileName"
        AppModule.uploadManager.enqueueUpload(uri, shareName, fullRemotePath)
        onSuccess()
    }

    // ─── Batch Operations ────────────────────────────────────────────────────────

    fun requestBatchDelete(items: Set<com.smbcore.model.SmbPath>) {
        if (items.isEmpty()) return
        _state.update { it.copy(batchTargetItems = items, showBatchDeleteDialog = true) }
    }

    fun executeBatchDelete(onSuccess: () -> Unit) {
        val items = _state.value.batchTargetItems.toList()
        _state.update { it.copy(showBatchDeleteDialog = false, batchTargetItems = emptySet()) }
        AppModule.fileOperationManager.deleteItems(items)
        onSuccess()
    }

    fun requestBatchCopy(items: Set<com.smbcore.model.SmbPath>) {
        if (items.isEmpty()) return
        AppModule.pendingBatchOperation.value = AppModule.PendingBatchOperation(AppModule.BatchOperationType.COPY, items)
        _state.update { it.copy(batchTargetItems = emptySet(), showActionSheet = false, successMessage = "Select destination to copy to") }
    }

    fun requestBatchMove(items: Set<com.smbcore.model.SmbPath>) {
        if (items.isEmpty()) return
        AppModule.pendingBatchOperation.value = AppModule.PendingBatchOperation(AppModule.BatchOperationType.MOVE, items)
        _state.update { it.copy(batchTargetItems = emptySet(), showActionSheet = false, successMessage = "Select destination to move to") }
    }

    fun executePendingOperation(destShare: String, destPath: String, onSuccess: () -> Unit) {
        val pending = AppModule.pendingBatchOperation.value ?: return
        AppModule.pendingBatchOperation.value = null
        val items = pending.items.toList()
        if (pending.type == AppModule.BatchOperationType.COPY) {
            AppModule.fileOperationManager.copyItems(items, destShare, destPath)
        } else {
            AppModule.fileOperationManager.moveItems(items, destShare, destPath)
        }
        _state.update { it.copy(successMessage = if (pending.type == AppModule.BatchOperationType.COPY) "Copying..." else "Moving...") }
        onSuccess()
    }
    
    fun cancelPendingOperation() {
        AppModule.pendingBatchOperation.value = null
    }
}
