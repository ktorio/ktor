package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.nio.channels.*

class ByteArrayWriteChannel : WriteChannel {
    private val baos = ByteArrayOutputStream()
    private val baosAsChannel = Channels.newChannel(baos)

    fun toByteArray() = baos.toByteArray()!!

    override fun close() {
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        handler.success(baosAsChannel.write(src))
    }
}