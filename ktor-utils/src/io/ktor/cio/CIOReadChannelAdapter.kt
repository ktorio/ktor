package io.ktor.cio

import kotlinx.coroutines.experimental.io.*
import io.ktor.cio.*
import java.nio.ByteBuffer

class CIOReadChannelAdapter(private val input: ByteReadChannel) : ReadChannel {
    suspend override fun read(dst: ByteBuffer): Int {
        return input.readAvailable(dst)
    }

    override fun close() {
    }
}
