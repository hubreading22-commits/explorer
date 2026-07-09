package com.smbcore.internal.directory

import com.hierynomus.smbj.share.DiskShare
import com.smbcore.internal.connection.ConnectionManagerImpl
import com.smbcore.internal.mapper.ModelMapper
import com.smbcore.model.FileItem
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult

internal class DirectoryServiceImpl(private val connectionManager: ConnectionManagerImpl) {

    fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>> {
        val session = connectionManager.session ?: return SmbResult.Failure(SmbError.NetworkUnavailable)

        return try {
            val share = session.connectShare(shareName) as? DiskShare
                ?: return SmbResult.Failure(SmbError.ShareNotFound)

            val results = mutableListOf<FileItem>()
            for (f in share.list(path, "*")) {
                if (f.fileName != "." && f.fileName != "..") {
                    results.add(ModelMapper.mapToFileItem(path, f))
                }
            }
            share.close()
            SmbResult.Success(results)
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            when {
                e.status.name.contains("ACCESS_DENIED") -> SmbResult.Failure(SmbError.PermissionDenied)
                e.status.name.contains("OBJECT_NAME_NOT_FOUND") -> SmbResult.Failure(SmbError.FileNotFound)
                else -> SmbResult.Failure(SmbError.Unknown(e.message ?: "Unknown SMB Error"))
            }
        } catch (e: Exception) {
            SmbResult.Failure(SmbError.Unknown("Failed to list directory: ${e.message}"))
        }
    }
}
