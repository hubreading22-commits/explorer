package com.smbcore.internal

import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult
import java.io.IOException
import java.net.SocketException
import com.hierynomus.mssmb2.SMBApiException

internal object ExceptionMapper {
    fun <T> map(e: Throwable, defaultMessage: String): SmbResult.Failure {
        return when (e) {
            is SMBApiException -> {
                when {
                    e.status.name.contains("ACCESS_DENIED") -> SmbResult.Failure(SmbError.PermissionDenied)
                    e.status.name.contains("OBJECT_NAME_NOT_FOUND") -> SmbResult.Failure(SmbError.FileNotFound)
                    e.status.name.contains("OBJECT_NAME_COLLISION") -> SmbResult.Failure(SmbError.AlreadyExists)
                    e.status.name.contains("LOGON_FAILURE") -> SmbResult.Failure(SmbError.AuthenticationFailed)
                    else -> SmbResult.Failure(SmbError.Unknown(e.message ?: defaultMessage))
                }
            }
            is IOException, is SocketException -> {
                SmbResult.Failure(SmbError.NetworkUnavailable)
            }
            else -> {
                SmbResult.Failure(SmbError.Unknown("$defaultMessage: ${e.message}"))
            }
        }
    }
}
