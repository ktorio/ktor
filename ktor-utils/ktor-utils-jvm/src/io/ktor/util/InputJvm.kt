package io.ktor.util

import kotlinx.io.core.*
import java.io.*

/**
 * Convert kotlinx.io [Input] to java [InputStream]
 */
@KtorExperimentalAPI
fun Input.asStream(): InputStream = object : InputStream() {

    override fun read(): Int = tryPeek()

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (this@asStream.endOfInput) return -1
        return readAvailable(buffer, offset, length)
    }

    override fun skip(count: Long): Long = discard(count)

    override fun close() {
        this@asStream.close()
    }
}

