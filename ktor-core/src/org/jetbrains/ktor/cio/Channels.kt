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

suspend fun ReadChannel.sendTo(out: WriteChannel, bufferSize: Int = 8192, bufferPool: ByteBufferPool = NoPool) {
    val ticket = bufferPool.allocate(bufferSize)
    try {
        while (read(ticket.buffer) != -1) {
            ticket.buffer.flip()
            out.write(ticket.buffer)
            ticket.buffer.clear()
        }
    } finally {
        bufferPool.release(ticket)
    }
}

