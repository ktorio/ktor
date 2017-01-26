package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*

interface Channel : Closeable

interface ReadChannel : Channel {
    suspend fun read(dst: ByteBuffer): Int
}

interface WriteChannel : Channel {
    suspend fun write(src: ByteBuffer)
}

interface RandomAccessReadChannel : ReadChannel {
    val position: Long
    val size: Long

    suspend fun seek(position: Long)
}

suspend fun ReadChannel.copyTo(out: WriteChannel, bufferSize: Int = 8192, bufferPool: ByteBufferPool = NoPool): Int {
    val ticket = bufferPool.allocate(bufferSize)
    var bytes = 0
    try {
        while (read(ticket.buffer).also { bytes += it } != -1) {
            ticket.buffer.flip()
            out.write(ticket.buffer)
            ticket.buffer.clear()
        }
    } finally {
        bufferPool.release(ticket)
    }
    return bytes + 1 // compensate for -1 as EOF
}

