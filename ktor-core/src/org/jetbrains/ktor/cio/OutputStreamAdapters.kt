package org.jetbrains.ktor.cio

import java.io.*
import java.nio.*

class OutputStreamChannel(val streamer: (OutputStream) -> Unit) : OutputStream(), ReadChannel {

    suspend override fun read(dst: ByteBuffer): Int {
        return -1
    }

    override fun write(b: Int) {

    }
}