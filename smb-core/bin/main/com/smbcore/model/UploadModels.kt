package com.smbcore.model

data class UploadProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val percentage: Float,
    val speedBytesPerSecond: Long,
    val estimatedRemainingSeconds: Long
)

sealed class UploadState {
    object Waiting : UploadState()
    object Uploading : UploadState()
    object Verifying : UploadState()
    object Renaming : UploadState()
    object Completed : UploadState()
    object Cancelled : UploadState()
    data class Failed(val error: String) : UploadState()
    object AlreadyExists : UploadState()
}
