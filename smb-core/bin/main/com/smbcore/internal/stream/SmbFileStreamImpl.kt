package com.smbcore.internal.stream

import com.hierynomus.smbj.share.File
import com.smbcore.io.FileStream
import java.io.InputStream

internal class SmbFileStreamImpl(
    private var smbFile: File,
    private val fileSize: Long,
    private val reopener: (() -> File)? = null
) : FileStream {

    private var inputStream: InputStream = smbFile.inputStream
    private var currentPosition: Long = 0

    override fun read(buffer: ByteArray): Int {
        return try {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead > 0) {
                currentPosition += bytesRead
            }
            bytesRead
        } catch (e: java.io.IOException) {
            if (reopener == null) throw e
            try {
                // Attempt to reopen the stream
                smbFile = reopener.invoke()
                inputStream = smbFile.inputStream
                if (currentPosition > 0) {
                    val skipped = inputStream.skip(currentPosition)
                    if (skipped != currentPosition) throw java.io.IOException("Failed to seek after reconnect")
                }
                // Retry read once
                val bytesRead = inputStream.read(buffer)
                if (bytesRead > 0) {
                    currentPosition += bytesRead
                }
                bytesRead
            } catch (reconnectEx: Exception) {
                throw java.io.IOException("Failed to read and reconnect", reconnectEx)
            }
        }
    }

    override fun seek(position: Long) {
        if (position < currentPosition) {
            if (reopener != null) {
                try {
                    smbFile = reopener.invoke()
                    inputStream = smbFile.inputStream
                    currentPosition = 0
                } catch (e: Exception) {
                    throw UnsupportedOperationException("Failed to reopen stream for backward seek", e)
                }
            } else {
                throw UnsupportedOperationException("Backward seeking requires reopening the SMB stream.")
            }
        }
        val bytesToSkip = position - currentPosition
        if (bytesToSkip > 0) {
            val skipped = inputStream.skip(bytesToSkip)
            currentPosition += skipped
        }
    }

    override fun length(): Long {
        return fileSize
    }

    override fun position(): Long {
        return currentPosition
    }

    override fun close() {
        try { inputStream.close() } catch (e: Exception) {}
        try { smbFile.close() } catch (e: Exception) {}
    }
}
