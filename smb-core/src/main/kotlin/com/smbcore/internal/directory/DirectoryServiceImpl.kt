package com.smbcore.internal.directory

import com.hierynomus.smbj.share.DiskShare
import com.smbcore.internal.connection.ConnectionManagerImpl
import com.smbcore.internal.ExceptionMapper
import com.smbcore.internal.mapper.ModelMapper
import com.smbcore.model.FileItem
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult

internal class DirectoryServiceImpl(private val connectionManager: ConnectionManagerImpl) {

    suspend fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>> {
        val connected = connectionManager.ensureConnected()
        if (connected is SmbResult.Failure) return connected
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
        } catch (e: Exception) {
            ExceptionMapper.map(e, "Failed to list directory")
        }
    }
}
