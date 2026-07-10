package com.smbcore.io

interface FileStream : AutoCloseable {
    fun read(buffer: ByteArray): Int
    fun seek(position: Long)
    fun length(): Long
    fun position(): Long
    override fun close()
}

fun FileStream.inputStream(): java.io.InputStream = object : java.io.InputStream() {
    override fun read(): Int {
        val buf = ByteArray(1)
        val read = this@inputStream.read(buf)
        return if (read == -1 || read == 0) -1 else buf[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val buf = ByteArray(len)
        val read = this@inputStream.read(buf)
        if (read == -1 || read == 0) return -1
        System.arraycopy(buf, 0, b, off, read)
        return read
    }

    override fun close() {
        this@inputStream.close()
    }
}
