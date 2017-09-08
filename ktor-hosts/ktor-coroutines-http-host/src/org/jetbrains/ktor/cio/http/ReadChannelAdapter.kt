package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.io.*
import org.jetbrains.ktor.cio.*
import java.nio.ByteBuffer

internal class ReadChannelAdapter(private val input: ByteReadChannel) : ReadChannel {
    suspend override fun read(dst: ByteBuffer): Int {
        return input.readAvailable(dst)
    }

    override fun close() {
    }
}
