package io.ktor.utils.io

import java.nio.*

/**
 * Creates channel for reading from the specified byte buffer.
 */
fun ByteReadChannel(content: ByteBuffer): ByteReadChannel = ByteBufferChannel(content)

/**
 * Creates buffered channel for asynchronous reading and writing of sequences of bytes.
 */
actual fun ByteChannel(autoFlush: Boolean): ByteChannel = ByteBufferChannel(autoFlush = autoFlush)


/**
 * Creates channel for reading from the specified byte array.
 */
actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel =
        ByteBufferChannel(ByteBuffer.wrap(content, offset, length))

