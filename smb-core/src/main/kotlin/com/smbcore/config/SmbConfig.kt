package com.smbcore.config

data class SmbConfig(
    val serverIP: String,
    val bufferSize: Int = 4096,
    val transferBufferSize: Int = 1048576,
    val connectTimeout: Long = 10000,
    val readTimeout: Long = 10000,
    val writeTimeout: Long = 10000,
    val retryCount: Int = 3
)
