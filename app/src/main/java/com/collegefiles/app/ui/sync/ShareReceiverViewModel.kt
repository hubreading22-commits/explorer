package com.collegefiles.app.ui.sync

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.collegefiles.app.di.AppModule
import com.collegefiles.app.sync.ShareResolveResult
import com.collegefiles.app.upload.UploadManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ShareReceiverState {
    object Idle : ShareReceiverState()
    data class Resolving(val uris: List<Uri>) : ShareReceiverState()
    data class Conflict(val uri: Uri, val session: com.collegefiles.app.sync.DocumentSession, val nextUris: List<Uri>) : ShareReceiverState()
    data class NoMatch(val uri: Uri, val fileName: String, val nextUris: List<Uri>) : ShareReceiverState()
    object Done : ShareReceiverState()
}

class ShareReceiverViewModel(
    private val shareImportManager: com.collegefiles.app.sync.ShareImportManager = AppModule.shareImportManager,
    private val uploadManager: UploadManager = AppModule.uploadManager
) : ViewModel() {

    private val _state = MutableStateFlow<ShareReceiverState>(ShareReceiverState.Idle)
    val state: StateFlow<ShareReceiverState> = _state.asStateFlow()

    fun handleSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _state.value = ShareReceiverState.Resolving(uris)
        processNextUri(uris)
    }

    private fun processNextUri(uris: List<Uri>) {
        if (uris.isEmpty()) {
            _state.value = ShareReceiverState.Done
            return
        }

        val uri = uris.first()
        val nextUris = uris.drop(1)

        val result = shareImportManager.resolveSession(uri)
        when (result) {
            is ShareResolveResult.MatchFound -> {
                _state.value = ShareReceiverState.Conflict(uri, result.session, nextUris)
            }
            is ShareResolveResult.NoMatch -> {
                _state.value = ShareReceiverState.NoMatch(uri, result.fileName, nextUris)
            }
        }
    }

    fun onReplace(uri: Uri, session: com.collegefiles.app.sync.DocumentSession, nextUris: List<Uri>) {
        viewModelScope.launch {
            uploadManager.enqueueUpload(uri, session.shareName, session.remotePath, overwrite = true)
            // Cleanup the active session since it's now updated? Or keep monitoring?
            // Usually we just upload it and let the server have the new version.
            processNextUri(nextUris)
        }
    }

    fun onKeepBoth(uri: Uri, session: com.collegefiles.app.sync.DocumentSession, nextUris: List<Uri>) {
        viewModelScope.launch {
            // Keep both: modify the remotePath to append something like "- Copy" or timestamp
            val newPath = session.remotePath.substringBeforeLast('.') + "-Copy." + session.remotePath.substringAfterLast('.')
            uploadManager.enqueueUpload(uri, session.shareName, newPath)
            processNextUri(nextUris)
        }
    }

    fun onCancel(nextUris: List<Uri>) {
        processNextUri(nextUris)
    }

    fun onFolderSelected(uri: Uri, fileName: String, shareName: String, folderPath: String, nextUris: List<Uri>) {
        val remotePath = if (folderPath.isEmpty()) fileName else "$folderPath\\$fileName"
        uploadManager.enqueueUpload(uri, shareName, remotePath)
        processNextUri(nextUris)
    }

    fun onManualUpload(uri: Uri, fileName: String, nextUris: List<Uri>) {
        AppModule.pendingUploads.value = Pair(uri, fileName)
        processNextUri(nextUris)
    }

    fun dismiss() {
        _state.value = ShareReceiverState.Idle
    }
}
