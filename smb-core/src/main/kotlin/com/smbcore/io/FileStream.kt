package com.smbcore.io

interface FileStream : AutoCloseable {
    fun read(buffer: ByteArray): Int
    fun seek(position: Long)
    fun length(): Long
    fun position(): Long
    override fun close()
}
