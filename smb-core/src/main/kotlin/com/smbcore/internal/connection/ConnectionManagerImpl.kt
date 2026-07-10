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
import java.util.concurrent.TimeUnit

internal class ConnectionManagerImpl(private val config: SmbConfig) {

    private var smbClient: SMBClient? = null
    var connection: Connection? = null
        private set
    var session: Session? = null
        private set
    private var currentUser: User? = null

    init {
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
        return try {
            if (isConnected()) {
                logout()
            }

            val conn = smbClient!!.connect(config.serverIP)
            this.connection = conn

            val ac = AuthenticationContext(credentials.username, credentials.password, credentials.domain)
            val sess = conn.authenticate(ac)
            this.session = sess

            val user = User(credentials.username, credentials.domain)
            this.currentUser = user

            SmbResult.Success(user)
        } catch (e: com.hierynomus.mssmb2.SMBApiException) {
            when {
                e.status.name.contains("LOGON_FAILURE") -> SmbResult.Failure(SmbError.AuthenticationFailed)
                e.status.name.contains("ACCESS_DENIED") -> SmbResult.Failure(SmbError.PermissionDenied)
                else -> SmbResult.Failure(SmbError.Unknown(e.message ?: "Unknown SMB Error"))
            }
        } catch (e: Throwable) {
            SmbResult.Failure(SmbError.Unknown(e.toString()))
        }
    }

    fun logout(): SmbResult<Unit> {
        return try {
            session?.logoff()
            connection?.close()
            session = null
            connection = null
            currentUser = null
            SmbResult.Success(Unit)
        } catch (e: Exception) {
            SmbResult.Failure(SmbError.Unknown("Failed to logout: ${e.message}"))
        }
    }

    fun isConnected(): Boolean {
        return session != null && connection?.isConnected == true
    }
}
