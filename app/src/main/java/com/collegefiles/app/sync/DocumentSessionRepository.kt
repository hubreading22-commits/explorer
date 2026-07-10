package com.collegefiles.app.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DocumentSessionRepository {
    private val _sessions = MutableStateFlow<Map<UUID, DocumentSession>>(emptyMap())
    val sessions: StateFlow<Map<UUID, DocumentSession>> = _sessions.asStateFlow()
    
    private val mutex = Mutex()

    suspend fun addSession(session: DocumentSession) = mutex.withLock {
        _sessions.update { current ->
            current + (session.sessionId to session)
        }
    }

    suspend fun getSession(sessionId: UUID): DocumentSession? = mutex.withLock {
        _sessions.value[sessionId]
    }
    
    suspend fun updateSession(sessionId: UUID, update: (DocumentSession) -> DocumentSession) = mutex.withLock {
        _sessions.update { current ->
            val session = current[sessionId] ?: return@update current
            current + (sessionId to update(session))
        }
    }

    suspend fun removeSession(sessionId: UUID) = mutex.withLock {
        _sessions.update { current ->
            current - sessionId
        }
    }

    suspend fun clearAll() = mutex.withLock {
        _sessions.update { emptyMap() }
    }
}
