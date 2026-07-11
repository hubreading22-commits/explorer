package com.collegefiles.app.di

import com.collegefiles.app.config.ContentPolicy
import com.smbcore.SmbClient
import com.smbcore.config.SmbConfig

object AppModule {
    val smbConfig = SmbConfig(
        serverIP = "192.168.1.50",
        bufferSize = 1048576, // 1MB buffer for much faster transfer speeds
        connectTimeout = 10000,
        readTimeout = 10000,
        writeTimeout = 10000
    )

    lateinit var credentialStore: com.smbcore.model.CredentialStore
    lateinit var uploadManager: com.collegefiles.app.upload.UploadManager
    val smbClient: com.smbcore.SmbClient by lazy {
        com.smbcore.SmbClient.create(smbConfig, credentialStore)
    }
        
    val acknowledgedWorkIds = mutableSetOf<java.util.UUID>()

    lateinit var contentPolicy: ContentPolicy
    lateinit var fileOperationManager: com.collegefiles.app.ops.FileOperationManager
    
    val documentSessionRepository = com.collegefiles.app.sync.DocumentSessionRepository()
    lateinit var documentSessionService: com.collegefiles.app.sync.DocumentSessionService
        private set
        
    lateinit var shareImportManager: com.collegefiles.app.sync.ShareImportManager
        private set
        
    val pendingUploads = kotlinx.coroutines.flow.MutableStateFlow<Pair<android.net.Uri, String>?>(null)

    fun initialize(context: android.content.Context) {
        documentSessionService = com.collegefiles.app.sync.DocumentSessionService(
            context = context,
            repository = documentSessionRepository,
            smbClient = smbClient,
            uploadManager = uploadManager
        )
        
        shareImportManager = com.collegefiles.app.sync.ShareImportManager(
            context = context,
            repository = documentSessionRepository
        )
        
        fileOperationManager = com.collegefiles.app.ops.FileOperationManager(smbClient)
    }
}
