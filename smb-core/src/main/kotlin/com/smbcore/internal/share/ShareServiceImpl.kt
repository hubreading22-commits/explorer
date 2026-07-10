package com.smbcore.internal.share

import com.rapid7.client.dcerpc.mssrvs.ServerService
import com.rapid7.client.dcerpc.transport.SMBTransportFactories
import com.smbcore.internal.connection.ConnectionManagerImpl
import com.smbcore.internal.ExceptionMapper
import com.smbcore.model.Share
import com.smbcore.model.SmbError
import com.smbcore.model.SmbResult

internal class ShareServiceImpl(private val connectionManager: ConnectionManagerImpl) {

    suspend fun listShares(): SmbResult<List<Share>> {
        var retries = 1
        while (true) {
            val connected = connectionManager.ensureConnected()
            if (connected is SmbResult.Failure) return connected
            val session = connectionManager.session ?: return SmbResult.Failure(SmbError.NetworkUnavailable)

            try {
                val transport = SMBTransportFactories.SRVSVC.getTransport(session)
                val serverService = ServerService(transport)
                val shares = serverService.getShares0()

                val shareList = shares.mapNotNull {
                    val netName = it.netName
                    if (netName == "IPC$" || netName.endsWith("$")) null
                    else Share(netName)
                }
                return SmbResult.Success(shareList)
            } catch (e: Exception) {
                if (retries > 0 && (e is java.io.IOException || e is com.hierynomus.mssmb2.SMBApiException)) {
                    connectionManager.invalidate()
                    retries--
                    continue
                }
                return ExceptionMapper.map(e, "Failed to list shares")
            }
        }
    }
}
