package com.collegefiles.app.di

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

    val smbClient: SmbClient by lazy {
        SmbClient.create(smbConfig)
    }

    lateinit var contentPolicy: ContentPolicy
}
