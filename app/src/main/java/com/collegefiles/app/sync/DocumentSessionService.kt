package com.collegefiles.app.sync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.FileObserver
import androidx.core.content.FileProvider
import com.collegefiles.app.upload.UploadManager
import com.smbcore.SmbClient
import com.smbcore.model.FileItem
import com.smbcore.model.SmbResult
import com.smbcore.io.inputStream
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class DocumentSessionService(
    private val context: Context,
    private val repository: DocumentSessionRepository,
    private val smbClient: SmbClient,
    private val uploadManager: UploadManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeObservers = mutableMapOf<UUID, FileObserver>()
    private val debounceJobs = mutableMapOf<UUID, Job>()
    private val pollingJobs = mutableMapOf<UUID, Job>()

    suspend fun openDocument(shareName: String, path: String, fileItem: FileItem): Result<Unit> {
        return try {
            val cacheDir = File(context.cacheDir, "sync_docs").apply { mkdirs() }
            val localFile = File(cacheDir, fileItem.name)

            val session = DocumentSession(
                shareName = shareName,
                remotePath = if (path.isEmpty()) fileItem.name else "$path\\${fileItem.name}",
                localFile = localFile
            )

            repository.addSession(session)
            repository.updateSession(session.sessionId) { it.copy(state = SessionState.PREPARING) }

            val result = smbClient.openFile(shareName, session.remotePath)

            if (result is SmbResult.Failure) {
                repository.updateSession(session.sessionId) { it.copy(state = SessionState.FAILED, error = result.error.toString()) }
                return Result.failure(Exception("Download failed: ${result.error}"))
            }

            val fileStream = (result as SmbResult.Success).data
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                fileStream.inputStream().use { input ->
                    java.io.FileOutputStream(localFile).use { output ->
                        input.copyTo(output)
                    }
                }
                fileStream.close()
            }

            repository.updateSession(session.sessionId) { it.copy(state = SessionState.CACHED) }

            val uri = FileProvider.getUriForFile(context, "com.collegefiles.app.fileprovider", localFile)
            val intent = Intent(Intent.ACTION_EDIT).apply {
                setDataAndType(uri, getMimeType(fileItem.name))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)

            repository.updateSession(session.sessionId) { it.copy(state = SessionState.OPENED) }

            startMonitoring(session.sessionId, localFile)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "pdf" -> "application/pdf"
            "docx", "doc" -> "application/msword"
            "xlsx", "xls" -> "application/vnd.ms-excel"
            "pptx", "ppt" -> "application/vnd.ms-powerpoint"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }

    private fun startMonitoring(sessionId: UUID, file: File) {
        var lastModified = file.lastModified()
        var lastSize = file.length()

        val pathStr = file.parentFile?.absolutePath ?: return
        val observer = object : FileObserver(pathStr, FileObserver.CLOSE_WRITE or FileObserver.MODIFY) {
            override fun onEvent(event: Int, path: String?) {
                if (path == file.name) {
                    onFileChanged(sessionId, file)
                }
            }
        }
        observer.startWatching()
        activeObservers[sessionId] = observer

        pollingJobs[sessionId] = scope.launch {
            while (isActive) {
                delay(5000)
                val currentModified = file.lastModified()
                val currentSize = file.length()
                if (currentModified != lastModified || currentSize != lastSize) {
                    lastModified = currentModified
                    lastSize = currentSize
                    onFileChanged(sessionId, file)
                }
            }
        }
    }

    private fun onFileChanged(sessionId: UUID, file: File) {
        scope.launch {
            repository.updateSession(sessionId) { it.copy(state = SessionState.MODIFIED, isDirty = true) }
            
            debounceJobs[sessionId]?.cancel()
            debounceJobs[sessionId] = launch {
                delay(3000)
                triggerUpload(sessionId)
            }
        }
    }

    private suspend fun triggerUpload(sessionId: UUID) {
        val session = repository.getSession(sessionId) ?: return
        repository.updateSession(sessionId) { it.copy(state = SessionState.UPLOADING) }
        
        val uri = FileProvider.getUriForFile(context, "com.collegefiles.app.fileprovider", session.localFile)
        val workId = uploadManager.enqueueUpload(uri, session.shareName, session.remotePath)
        repository.updateSession(sessionId) { it.copy(uploadJobId = workId) }
    }

    fun stopMonitoring(sessionId: UUID) {
        activeObservers.remove(sessionId)?.stopWatching()
        pollingJobs.remove(sessionId)?.cancel()
        debounceJobs.remove(sessionId)?.cancel()
    }

    suspend fun cleanupSession(sessionId: UUID) {
        stopMonitoring(sessionId)
        val session = repository.getSession(sessionId)
        session?.localFile?.delete()
        repository.removeSession(sessionId)
    }

    suspend fun clearAll() {
        val sessions = repository.sessions.value.keys.toList()
        for (id in sessions) {
            cleanupSession(id)
        }
        File(context.cacheDir, "sync_docs").deleteRecursively()
    }
}
