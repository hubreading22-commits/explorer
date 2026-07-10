package com.smbcore.internal.connection

import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig as SmbjConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.smbcore.config.SmbConfig
import com.smbcore.model.Credentials
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import com.smbcore.model.User
import com.smbcore.model.ConnectionState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

import com.smbcore.model.CredentialStore

internal class ConnectionManagerImpl(
    private val config: SmbConfig,
    private val credentialStore: CredentialStore?
) {

    private var smbClient: SMBClient? = null
    var connection: Connection? = null
        private set
    var session: Session? = null
        private set
    private var currentUser: User? = null
    
    private val mutex = Mutex()
    
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    init {
        recreateSmbClient()
    }

    private fun recreateSmbClient() {
        try { smbClient?.close() } catch (_: Exception) {}
        val smbjConfig = SmbjConfig.builder()
            .withReadBufferSize(config.bufferSize)
            .withWriteBufferSize(config.bufferSize)
            .withReadTimeout(config.readTimeout, TimeUnit.MILLISECONDS)
            .withWriteTimeout(config.writeTimeout, TimeUnit.MILLISECONDS)
            .withTransactTimeout(config.connectTimeout, TimeUnit.MILLISECONDS)
            .build()
        smbClient = SMBClient(smbjConfig)
    }

    fun login(credentials: Credentials): SmbResult<User> {
        _connectionState.value = ConnectionState.CONNECTING
        return try {
            // Always logout to ensure any stale sockets or sessions are closed
            logout()
            recreateSmbClient()

            val conn = smbClient!!.connect(config.serverIP)
            this.connection = conn

            val ac = AuthenticationContext(credentials.username, credentials.password.clone(), credentials.domain)
            val sess = conn.authenticate(ac)
            this.session = sess

            val user = User(credentials.username, credentials.domain)
            this.currentUser = user
            
            // Store credentials in persistent store (if available)
            credentialStore?.save(credentials)
            
            _connectionState.value = ConnectionState.CONNECTED

            SmbResult.Success(user)
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            when {
                e.status.name.contains("LOGON_FAILURE") -> {
                    _connectionState.value = ConnectionState.UNAUTHENTICATED
                    credentialStore?.clear()
                    SmbResult.Failure(SmbError.AuthenticationFailed)
                }
                e.status.name.contains("ACCESS_DENIED") -> {
                    _connectionState.value = ConnectionState.UNAUTHENTICATED
                    credentialStore?.clear()
                    SmbResult.Failure(SmbError.PermissionDenied)
                }
                else -> {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    SmbResult.Failure(SmbError.Unknown(e.message ?: "Unknown SMB Error"))
                }
            }
        } catch (e: java.io.IOException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            SmbResult.Failure(SmbError.NetworkUnavailable)
        } catch (e: java.net.SocketException) {
            _connectionState.value = ConnectionState.DISCONNECTED
            SmbResult.Failure(SmbError.NetworkUnavailable)
        } catch (e: Throwable) {
            _connectionState.value = ConnectionState.DISCONNECTED
            SmbResult.Failure(SmbError.Unknown(e.toString()))
        }
    }

    fun logout(): SmbResult<Unit> {
        return try {
            credentialStore?.clear()
            try { session?.logoff() } catch (_: Exception) {}
            try { connection?.close() } catch (_: Exception) {}
            session = null
            connection = null
            currentUser = null
            _connectionState.value = ConnectionState.DISCONNECTED
            SmbResult.Success(Unit)
        } catch (e: Exception) {
            _connectionState.value = ConnectionState.DISCONNECTED
            SmbResult.Failure(SmbError.Unknown("Failed to logout: ${e.message}"))
        }
    }

    fun isConnected(): Boolean {
        return session != null && connection?.isConnected == true
    }

    suspend fun ensureConnected(): SmbResult<Unit> {
        if (isConnected()) return SmbResult.Success(Unit)

        return mutex.withLock {
            // Check again inside lock
            if (isConnected()) return@withLock SmbResult.Success(Unit)

            val creds = credentialStore?.get()
            if (creds == null) {
                _connectionState.value = ConnectionState.UNAUTHENTICATED
                return@withLock SmbResult.Failure(SmbError.AuthenticationFailed)
            }

            _connectionState.value = ConnectionState.RECOVERING

            val delays = listOf(0L, 500L, 1000L)
            var lastError: SmbResult.Failure? = null

            for (delayMs in delays) {
                if (delayMs > 0) delay(delayMs)
                
                try {
                    // Force close old broken handles
                    try { session?.logoff() } catch (_: Exception) {}
                    try { connection?.close() } catch (_: Exception) {}
                    
                    recreateSmbClient()
                    
                    val conn = smbClient!!.connect(config.serverIP)
                    val ac = AuthenticationContext(
                        creds.username,
                        creds.password.clone(),
                        creds.domain
                    )
                    val sess = conn.authenticate(ac)
                    
                    this.connection = conn
                    this.session = sess
                    
                    _connectionState.value = ConnectionState.CONNECTED
                    return@withLock SmbResult.Success(Unit)
                } catch (e: com.hierynomus.mssmb2.SMBApiException) {
                    if (e.status.name.contains("LOGON_FAILURE") || e.status.name.contains("ACCESS_DENIED")) {
                        credentialStore?.clear()
                        _connectionState.value = ConnectionState.UNAUTHENTICATED
                        return@withLock SmbResult.Failure(SmbError.AuthenticationFailed)
                    }
                    lastError = SmbResult.Failure(SmbError.Unknown(e.message ?: "Unknown SMB Error"))
                } catch (e: java.io.IOException) {
                    lastError = SmbResult.Failure(SmbError.NetworkUnavailable)
                } catch (e: java.net.SocketException) {
                    lastError = SmbResult.Failure(SmbError.NetworkUnavailable)
                } catch (e: Exception) {
                    lastError = SmbResult.Failure(SmbError.Unknown(e.message ?: "Unknown Error"))
                }
            }
            
            _connectionState.value = ConnectionState.DISCONNECTED
            lastError ?: SmbResult.Failure(SmbError.NetworkUnavailable)
        }
    }
}
