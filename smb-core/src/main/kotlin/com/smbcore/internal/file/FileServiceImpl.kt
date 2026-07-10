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

    suspend fun upload(input: InputStream, shareName: String, remotePath: String): SmbResult<Unit> = withShare(shareName) { share ->
        val file = share.openFile(
            remotePath,
            java.util.EnumSet.of(AccessMask.GENERIC_WRITE),
            java.util.EnumSet.of(FileAttributes.FILE_ATTRIBUTE_NORMAL),
            SMB2ShareAccess.ALL,
            SMB2CreateDisposition.FILE_OVERWRITE_IF,
            java.util.EnumSet.of(SMB2CreateOptions.FILE_NON_DIRECTORY_FILE)
        )
        file.outputStream.use { output ->
            input.copyTo(output, config.bufferSize)
        }
        file.close()
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
