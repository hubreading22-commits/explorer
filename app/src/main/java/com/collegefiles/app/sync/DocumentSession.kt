package com.collegefiles.app.sync

import java.io.File
import java.util.UUID

enum class SessionState {
    PREPARING,
    CACHED,
    OPENED,
    MODIFIED,
    UPLOADING,
    VERIFIED,
    COMPLETED,
    FAILED
}

data class DocumentSession(
    val sessionId: UUID = UUID.randomUUID(),
    val shareName: String,
    val remotePath: String,
    val localFile: File,
    val openedAt: Long = System.currentTimeMillis(),
    var lastSyncedAt: Long = 0L,
    var state: SessionState = SessionState.PREPARING,
    var isDirty: Boolean = false,
    var uploadJobId: UUID? = null,
    var error: String? = null
)
