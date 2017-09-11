package org.jetbrains.ktor.cio.http

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*

internal class OutputStreamAdapter(private val output: ByteWriteChannel, private val suppressClose: Boolean) : OutputStream() {
    override fun write(b: Int) = runBlocking {
        output.writeByte(b)
    }

    override fun write(b: ByteArray, off: Int, len: Int) = runBlocking {
        output.writeFully(b, off, len)
    }

    override fun flush() {
        output.flush()
    }

    override fun close() {
        if (suppressClose) output.flush()
        else output.close()
    }
}