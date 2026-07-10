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
    
    val smbClient: SmbClient by lazy {
        SmbClient.create(smbConfig, credentialStore)
    }

    lateinit var contentPolicy: ContentPolicy
}
