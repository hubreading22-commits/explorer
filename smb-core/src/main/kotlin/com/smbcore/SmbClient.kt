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
    
    fun listShares(): SmbResult<List<Share>>
    fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>>
    
    fun getMetadata(shareName: String, path: String): SmbResult<FileMetadata>
    fun openFile(shareName: String, path: String): SmbResult<FileStream>
    
    fun upload(input: InputStream, shareName: String, remotePath: String): SmbResult<Unit>
    fun delete(shareName: String, path: String): SmbResult<Unit>
    fun rename(shareName: String, oldPath: String, newName: String): SmbResult<Unit>
    fun copy(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit>
    fun move(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit>
    fun createFolder(shareName: String, path: String): SmbResult<Unit>
    
    companion object {
        fun create(config: SmbConfig): SmbClient {
            return SmbClientImpl(config)
        }
    }
}

internal class SmbClientImpl(
    private val config: SmbConfig
) : SmbClient {

    private val connectionManager = ConnectionManagerImpl(config)
    private val shareService = ShareServiceImpl(connectionManager)
    private val directoryService = DirectoryServiceImpl(connectionManager)
    private val fileService = FileServiceImpl(connectionManager, config)

    override fun login(credentials: Credentials): SmbResult<User> {
        return connectionManager.login(credentials)
    }

    override fun logout(): SmbResult<Unit> {
        return connectionManager.logout()
    }

    override fun isConnected(): Boolean {
        return connectionManager.isConnected()
    }

    override fun listShares(): SmbResult<List<Share>> {
        return shareService.listShares()
    }

    override fun listDirectory(shareName: String, path: String): SmbResult<List<FileItem>> {
        return directoryService.listDirectory(shareName, path)
    }

    override fun getMetadata(shareName: String, path: String): SmbResult<FileMetadata> {
        return fileService.getMetadata(shareName, path)
    }

    override fun openFile(shareName: String, path: String): SmbResult<FileStream> {
        return fileService.openFile(shareName, path)
    }

    override fun upload(input: InputStream, shareName: String, remotePath: String): SmbResult<Unit> {
        return fileService.upload(input, shareName, remotePath)
    }

    override fun delete(shareName: String, path: String): SmbResult<Unit> {
        return fileService.delete(shareName, path)
    }

    override fun rename(shareName: String, oldPath: String, newName: String): SmbResult<Unit> {
        return fileService.rename(shareName, oldPath, newName)
    }

    override fun copy(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        return fileService.copy(sourceShare, sourcePath, destShare, destPath)
    }

    override fun move(sourceShare: String, sourcePath: String, destShare: String, destPath: String): SmbResult<Unit> {
        return fileService.move(sourceShare, sourcePath, destShare, destPath)
    }

    override fun createFolder(shareName: String, path: String): SmbResult<Unit> {
        return fileService.createFolder(shareName, path)
    }
}
