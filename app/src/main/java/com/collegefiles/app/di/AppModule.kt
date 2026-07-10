package com.collegefiles.app.di

import com.collegefiles.app.config.ContentPolicy
import com.smbcore.SmbClient
import com.smbcore.config.SmbConfig

object AppModule {
    val smbConfig = SmbConfig(
        serverIP = "192.168.1.50",
        bufferSize = 4096,
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
    
    val documentSessionRepository = com.collegefiles.app.sync.DocumentSessionRepository()
    lateinit var documentSessionService: com.collegefiles.app.sync.DocumentSessionService
        private set

    fun initialize(context: android.content.Context) {
        documentSessionService = com.collegefiles.app.sync.DocumentSessionService(
            context = context,
            repository = documentSessionRepository,
            smbClient = smbClient,
            uploadManager = uploadManager
        )
    }
}
