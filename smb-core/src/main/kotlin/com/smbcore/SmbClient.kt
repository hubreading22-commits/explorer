package com.smbcore

import com.smbcore.config.SmbConfig
import com.smbcore.io.FileStream
import com.smbcore.model.*
import java.io.InputStream
import com.smbcore.internal.connection.ConnectionManagerImpl
import com.smbcore.internal.directory.DirectoryServiceImpl
import com.smbcore.internal.file.FileServiceImpl
import com.smbcore.internal.share.ShareServiceImpl

interface SmbClient {
    fun login(credentials: Credentials): SmbResult<User>
    fun logout(): SmbResult<Unit>
    fun isConnected(): Boolean
    
    val connectionState: kotlinx.coroutines.flow.StateFlow<ConnectionState>
    
    suspend fun listShares(): SmbResult<List<Share>>
    suspend fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>>
    
    suspend fun getMetadata(shareName: String, path: String): SmbResult<FileMetadata>
    suspend fun openFile(shareName: String, path: String): SmbResult<FileStream>
    
    suspend fun upload(input: InputStream, shareName: String, remotePath: String): SmbResult<Unit>
    suspend fun delete(shareName: String, path: String): SmbResult<Unit>
    suspend fun rename(shareName: String, oldPath: String, newName: String): SmbResult<Unit>
    suspend fun copy(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit>
    suspend fun move(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit>
    suspend fun createFolder(shareName: String, path: String): SmbResult<Unit>
    
    companion object {
        fun create(config: SmbConfig, credentialStore: CredentialStore? = null): SmbClient {
            return SmbClientImpl(config, credentialStore)
        }
    }
}

internal class SmbClientImpl(
    private val config: SmbConfig,
    private val credentialStore: CredentialStore?
) : SmbClient {

    private val connectionManager = ConnectionManagerImpl(config, credentialStore)
    private val shareService = ShareServiceImpl(connectionManager)
    private val directoryService = DirectoryServiceImpl(connectionManager)
    private val fileService = FileServiceImpl(connectionManager, config)

    override val connectionState: kotlinx.coroutines.flow.StateFlow<ConnectionState>
        get() = connectionManager.connectionState

    override fun login(credentials: Credentials): SmbResult<User> {
        return connectionManager.login(credentials)
    }

    override fun logout(): SmbResult<Unit> {
        return connectionManager.logout()
    }

    override fun isConnected(): Boolean {
        return connectionManager.isConnected()
    }

    override suspend fun listShares(): SmbResult<List<Share>> {
        return shareService.listShares()
    }

    override suspend fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>> {
        return directoryService.listDirectory(shareName, path)
    }

    override suspend fun getMetadata(shareName: String, path: String): SmbResult<FileMetadata> {
        return fileService.getMetadata(shareName, path)
    }

    override suspend fun openFile(shareName: String, path: String): SmbResult<FileStream> {
        return fileService.openFile(shareName, path)
    }

    override suspend fun upload(input: InputStream, shareName: String, remotePath: String): SmbResult<Unit> {
        return fileService.upload(input, shareName, remotePath)
    }

    override suspend fun delete(shareName: String, path: String): SmbResult<Unit> {
        return fileService.delete(shareName, path)
    }

    override suspend fun rename(shareName: String, oldPath: String, newName: String): SmbResult<Unit> {
        return fileService.rename(shareName, oldPath, newName)
    }

    override suspend fun copy(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        return fileService.copy(sourceShare, sourcePath, destShare, destPath)
    }

    override suspend fun move(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        return fileService.move(sourceShare, sourcePath, destShare, destPath)
    }

    override suspend fun createFolder(shareName: String, path: String): SmbResult<Unit> {
        return fileService.createFolder(shareName, path)
    }
}
