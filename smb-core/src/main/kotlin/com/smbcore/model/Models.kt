package com.smbcore.model

import java.time.Instant

data class Share(
    val name: String
)

enum class FileType {
    FOLDER, PDF, IMAGE, VIDEO, AUDIO, WORD, EXCEL, POWERPOINT, ZIP, TEXT, OFFICE, UNKNOWN
}

data class FileItem(
    val path: String,
    val name: String,
    val type: FileType,
    val size: Long,
    val modified: Instant,
    val isDirectory: Boolean
)

data class FileMetadata(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modified: Instant,
    val created: Instant,
    val permissions: Int,
    val owner: String
)

data class User(
    val username: String,
    val domain: String
)

sealed class SmbError {
    data object AuthenticationFailed : SmbError()
    data object PermissionDenied : SmbError()
    data object NetworkUnavailable : SmbError()
    data object FileNotFound : SmbError()
    data object ShareNotFound : SmbError()
    data class Unknown(val message: String) : SmbError()
}

sealed class SmbResult<out T> {
    data class Success<T>(val data: T) : SmbResult<T>()
    data class Failure(val error: SmbError) : SmbResult<Nothing>()
}
