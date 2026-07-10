package com.smbcore.internal.file

import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.share.DiskShare
import com.smbcore.config.SmbConfig
import com.smbcore.internal.connection.ConnectionManagerImpl
import com.smbcore.internal.stream.SmbFileStreamImpl
import com.smbcore.internal.ExceptionMapper
import com.smbcore.io.FileStream
import com.smbcore.model.FileMetadata
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import com.smbcore.model.UploadProgress
import com.smbcore.model.UploadState
import kotlinx.coroutines.isActive
import java.io.InputStream
import java.time.Instant

internal class FileServiceImpl(
    private val connectionManager: ConnectionManagerImpl,
    private val config: SmbConfig
) {
    private suspend fun <T> withShare(shareName: String, block: suspend (DiskShare) -> SmbResult<T>): SmbResult<T> {
        val connected = connectionManager.ensureConnected()
        if (connected is SmbResult.Failure) return connected
        val session = connectionManager.session ?: return SmbResult.Failure(SmbError.NetworkUnavailable)
        
        return try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return SmbResult.Failure(SmbError.ShareNotFound)
            val result = block(share)
            // Note: In a real SDK, shares might be cached or kept open. Here we close after ops.
            // Except for openFile which keeps the share open conceptually, but closing DiskShare 
            // doesn't immediately kill active file handles in SMBJ (it might on some servers).
            // For simplicity, we skip closing the share explicitly here or manage it carefully.
            result
        } catch (e: Exception) {
            ExceptionMapper.map(e, "Failed operation")
        }
    }

    suspend fun getMetadata(shareName: String, path: String): SmbResult<FileMetadata> = withShare(shareName) { share ->
        val info = share.getFileInformation(path)
        val isDir = (info.basicInformation.fileAttributes and FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value) != 0L
        val metadata = FileMetadata(
            path = path,
            name = path.substringAfterLast('\\', path),
            isDirectory = isDir,
            size = info.standardInformation.endOfFile,
            modified = Instant.ofEpochMilli(info.basicInformation.lastWriteTime.toEpochMillis()),
            created = Instant.ofEpochMilli(info.basicInformation.creationTime.toEpochMillis()),
            permissions = info.basicInformation.fileAttributes.toInt(),
            owner = ""
        )
        SmbResult.Success(metadata)
    }

    suspend fun openFile(shareName: String, path: String): SmbResult<FileStream> {
        val result = withShare(shareName) { share ->
            val file = share.openFile(
                path,
                java.util.EnumSet.of(AccessMask.GENERIC_READ),
                java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
            )
            SmbResult.Success(file)
        }
        if (result is SmbResult.Failure) return result
        
        val initialFile = (result as SmbResult.Success).data
        val sizeInfo = getMetadata(shareName, path)
        val size = if (sizeInfo is SmbResult.Success) sizeInfo.data.size else 0L

        val reopener: () -> com.hierynomus.smbj.share.File = {
            kotlinx.coroutines.runBlocking {
                val r = withShare(shareName) { share ->
                    val f = share.openFile(
                        path,
                        java.util.EnumSet.of(AccessMask.GENERIC_READ),
                        java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                    )
                    SmbResult.Success(f)
                }
                if (r is SmbResult.Failure) {
                    throw java.io.IOException("Failed to reconnect stream: ${r.error}")
                }
                (r as SmbResult.Success).data
            }
        }

        return SmbResult.Success(SmbFileStreamImpl(initialFile, size, reopener))
    }

    suspend fun upload(
        input: InputStream, 
        shareName: String, 
        remotePath: String, 
        expectedSize: Long,
        onProgress: suspend (UploadProgress) -> Unit,
        onStateChange: suspend (UploadState) -> Unit
    ): SmbResult<Unit> = withShare(shareName) { share ->
        
        if (share.fileExists(remotePath)) {
            onStateChange(UploadState.AlreadyExists)
            return@withShare SmbResult.Failure(SmbError.Unknown("File already exists"))
        }

        val tempPath = "$remotePath.uploading"
        onStateChange(UploadState.Uploading)
        
        val file = share.openFile(
            tempPath,
            java.util.EnumSet.of(AccessMask.GENERIC_WRITE, AccessMask.DELETE),
            java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        
        var success = false
        try {
            file.outputStream.use { output ->
                val buffer = ByteArray(config.bufferSize)
                var bytesRead: Int
                var totalTransferred = 0L
                val startTime = System.currentTimeMillis()
                var lastReportTime = startTime
                
                while (kotlinx.coroutines.currentCoroutineContext().isActive) {
                    bytesRead = input.read(buffer)
                    if (bytesRead < 0) break
                    
                    output.write(buffer, 0, bytesRead)
                    totalTransferred += bytesRead

                    val now = System.currentTimeMillis()
                    if (now - lastReportTime > 250) { // report every 250ms
                        val elapsedSec = (now - startTime) / 1000f
                        val speed = if (elapsedSec > 0) (totalTransferred / elapsedSec).toLong() else 0L
                        val remaining = expectedSize - totalTransferred
                        val eta = if (speed > 0) remaining / speed else 0L
                        val pct = if (expectedSize > 0) (totalTransferred.toFloat() / expectedSize) * 100f else 0f
                        
                        onProgress(
                            UploadProgress(
                                bytesTransferred = totalTransferred,
                                totalBytes = expectedSize,
                                percentage = pct,
                                speedBytesPerSecond = speed,
                                estimatedRemainingSeconds = eta
                            )
                        )
                        lastReportTime = now
                    }
                }
                
                if (!kotlinx.coroutines.currentCoroutineContext().isActive) {
                    throw kotlinx.coroutines.CancellationException("Upload cancelled")
                }
            }
            success = true
        } catch (e: Exception) {
            onStateChange(if (e is kotlinx.coroutines.CancellationException) UploadState.Cancelled else UploadState.Failed(e.message ?: "Unknown error"))
            try { file.close() } catch (_: Exception) {}
            try { share.rm(tempPath) } catch (_: Exception) {}
            if (e is kotlinx.coroutines.CancellationException) {
                return@withShare SmbResult.Failure(SmbError.Unknown("Upload cancelled"))
            } else {
                return@withShare SmbResult.Failure(SmbError.Unknown("Upload failed: ${e.message}"))
            }
        } finally {
            if (success) {
                try { file.close() } catch (_: Exception) {}
            }
        }

        if (success) {
            onStateChange(UploadState.Verifying)
            val info = share.getFileInformation(tempPath)
            val uploadedSize = info.standardInformation.endOfFile
            if (uploadedSize != expectedSize) {
                onStateChange(UploadState.Failed("Size mismatch: expected $expectedSize, got $uploadedSize"))
                try { share.rm(tempPath) } catch (_: Exception) {}
                return@withShare SmbResult.Failure(SmbError.Unknown("Upload verification failed (size mismatch)"))
            }
            
            onStateChange(UploadState.Renaming)
            val renameFile = share.openFile(
                tempPath,
                java.util.EnumSet.of(AccessMask.DELETE),
                null,
                SMB2ShareAccess.ALL,
                SMB2CreateDisposition.FILE_OPEN,
                null
            )
            renameFile.rename(remotePath)
            renameFile.close()
            onStateChange(UploadState.Completed)
        }
        
        SmbResult.Success(Unit)
    }

    suspend fun delete(shareName: String, path: String): SmbResult<Unit> = withShare(shareName) { share ->
        share.rm(path)
        SmbResult.Success(Unit)
    }

    suspend fun rename(shareName: String, oldPath: String, newName: String): SmbResult<Unit> = withShare(shareName) { share ->
        val newPath = oldPath.substringBeforeLast('\\', "") + "\\" + newName
        // SMBJ rename
        val file = share.openFile(
            oldPath,
            java.util.EnumSet.of(AccessMask.DELETE),
            null,
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OPEN,
            null
        )
        file.rename(newPath)
        file.close()
        SmbResult.Success(Unit)
    }

    suspend fun copy(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        val result = withShare(sourceShare) { sShare ->
            withShare(destShare) { dShare ->
                try {
                    val sFile = sShare.openFile(
                        sourcePath,
                        java.util.EnumSet.of(AccessMask.GENERIC_READ),
                        java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                    )
                    
                    val dFile = dShare.openFile(
                        destPath,
                        java.util.EnumSet.of(AccessMask.GENERIC_WRITE),
                        java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OVERWRITE_IF,
                        java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
                    )
                    
                    sFile.inputStream.use { input ->
                        dFile.outputStream.use { output ->
                            input.copyTo(output, config.bufferSize)
                        }
                    }
                    sFile.close()
                    dFile.close()
                    SmbResult.Success(Unit)
                } catch (e: Exception) {
                    SmbResult.Failure(SmbError.Unknown("Copy failed: ${e.message}"))
                }
            }
        }
        return result
    }

    suspend fun move(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        if (sourceShare == destShare) {
            // Intra-share move (rename)
            return withShare(sourceShare) { share ->
                try {
                    val file = share.openFile(
                        sourcePath,
                        java.util.EnumSet.of(AccessMask.DELETE),
                        null,
                        SMB2ShareAccess.ALL,
                        SMB2CreateDisposition.FILE_OPEN,
                        null
                    )
                    file.rename(destPath)
                    file.close()
                    SmbResult.Success(Unit)
                } catch (e: Exception) {
                    SmbResult.Failure(SmbError.Unknown("Move failed: ${e.message}"))
                }
            }
        } else {
            // Cross-share move (copy then delete)
            val copyResult = copy(sourceShare, sourcePath, destShare, destPath)
            if (copyResult is SmbResult.Success) {
                return delete(sourceShare, sourcePath)
            }
            return copyResult
        }
    }

    suspend fun createFolder(shareName: String, path: String): SmbResult<Unit> = withShare(shareName) { share ->
        share.mkdir(path)
        SmbResult.Success(Unit)
    }
}
