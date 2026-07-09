package com.smbcore.internal.stream

import com.hierynomus.smbj.share.File
import com.smbcore.io.FileStream
import java.io.InputStream

internal class SmbFileStreamImpl(
    private val smbFile: File,
    private val fileSize: Long
) : FileStream {

    private val inputStream: InputStream = smbFile.inputStream
    private var currentPosition: Long = 0

    override fun read(buffer: ByteArray): Int {
        val bytesRead = inputStream.read(buffer)
        if (bytesRead > 0) {
            currentPosition += bytesRead
        }
        return bytesRead
    }

    override fun seek(position: Long) {
        // SMBJ InputStream doesn't natively support full seek without reopening or skip.
        // A robust implementation would recreate the stream with an offset.
        // For now, we skip forward if possible, or throw if seeking backwards.
        if (position < currentPosition) {
            throw UnsupportedOperationException("Backward seeking requires reopening the SMB stream (not implemented in this stub).")
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
