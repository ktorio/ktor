/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.kron

import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.PayloadTooLargeException
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ClosedWriteChannelException
import io.ktor.utils.io.InternalAPI
import kotlinx.io.Buffer
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.UnsafeIoApi
import kotlinx.io.unsafe.UnsafeBufferOperations
import kron.glyph11.ByteBuilder
import kron.glyph11.Glyph11
import kron.glyph11.HttpParseException

/**
 * A request's body as Ktor reads it, pulled through the session's carry as the application asks for it: a
 * Content-Length body up to its length, a chunked one decoded chunk by chunk, or, after an upgrade, the
 * connection's bytes until the peer closes ([raw]).
 */
@OptIn(InternalAPI::class)
internal class RequestBody(
    private val session: Http1Session,
    private val chunked: Boolean,
    private var remaining: Long,
    private var expectContinue: Boolean,
    private val raw: Boolean = false,
) : ByteReadChannel {
    private val buffer = Buffer()
    private val decoded = if (chunked) ByteBuilder() else null
    private var cause: Throwable? = null

    /** Every byte of the body has left the connection, into [readBuffer]. */
    var isComplete: Boolean = false
        private set

    override val readBuffer: Source get() = buffer

    override val closedCause: Throwable? get() = cause

    override val isClosedForRead: Boolean get() = cause != null || (isComplete && buffer.exhausted())

    override suspend fun awaitContent(min: Int): Boolean {
        cause?.let { throw it }
        while (buffer.size < min && !isComplete) {
            when {
                raw -> pullRaw()
                chunked -> pullChunks()
                else -> pullLength()
            }
        }

        return buffer.size >= min
    }

    override fun cancel(cause: Throwable?) {
        if (this.cause != null) {
            return
        }

        this.cause = cause ?: IOException("Channel was cancelled")
        buffer.clear()
        if (!isComplete) {
            session.mustClose = true
        }
    }

    private suspend fun pullLength() {
        if (session.available == 0) {
            await()
        }

        val n = minOf(remaining, session.available.toLong()).toInt()
        buffer.write(session.carry, session.start, session.start + n)
        session.start += n
        remaining -= n
        if (remaining == 0L) {
            isComplete = true
        }
    }

    // Complete chunks only: one that has not all arrived waits in the carry, up to MAX_CHUNK_WAIT bytes of it.
    private suspend fun pullChunks() {
        val decoded = decoded!!
        while (true) {
            val held = session.available
            if (held > 0) {
                val result = try {
                    Glyph11.decodeChunks(session.carry, session.start, held, decoded)
                } catch (e: HttpParseException) {
                    throw fail(BadRequestException(e.message ?: "Malformed chunked body"))
                }

                if (decoded.length > 0) {
                    buffer.write(decoded.array, 0, decoded.length)
                    decoded.reset()
                }

                if (result >= 0) {
                    session.start += result
                    isComplete = true
                    return
                }

                val consumed = -result - 1
                session.start += consumed
                if (consumed > 0) {
                    return
                }

                if (held >= MAX_CHUNK_WAIT) {
                    throw fail(PayloadTooLargeException(MAX_CHUNK_WAIT.toLong()))
                }
            }

            await()
        }
    }

    private suspend fun pullRaw() {
        if (session.available == 0 && !session.fill()) {
            isComplete = true
            return
        }

        buffer.write(session.carry, session.start, session.end)
        session.start = session.end
    }

    private suspend fun await() {
        if (expectContinue) {
            expectContinue = false
            session.sendContinue()
        }

        if (!session.fill()) {
            throw fail(EOFException("The peer closed the connection before the request body ended"))
        }
    }

    private fun fail(e: Throwable): Throwable {
        cause = e
        session.mustClose = true
        return e
    }

    private companion object {
        const val MAX_CHUNK_WAIT = 1024 * 1024
    }
}

/**
 * A response's body as Ktor streams it: bytes collect in [writeBuffer] and are staged in the connection's write slab
 * on [flush], which sends them, framed as chunks when the response is chunked; [flushAndClose] stages the rest and
 * the last chunk, which the session sends with whatever follows. A HEAD response's body is dropped ([discard]).
 * Both take the session's send lock: a coroutine of the call may be reading the request body, and flushing, meanwhile.
 */
@OptIn(InternalAPI::class, UnsafeIoApi::class)
internal class ResponseBody(
    private val session: Http1Session,
    private val chunked: Boolean,
    private val contentLength: Long,
    private val discard: Boolean,
) : ByteWriteChannel {
    private val buffer = Buffer()
    private var closed = false
    private var cause: Throwable? = null
    private var written = 0L

    /** Closed with every byte the response announced: the connection can carry another response. */
    val isComplete: Boolean get() = closed && cause == null && (contentLength < 0 || written == contentLength)

    override val writeBuffer: Sink
        get() {
            if (closed) {
                throw ClosedWriteChannelException(cause)
            }

            return buffer
        }

    override val isClosedForWrite: Boolean get() = closed

    override val closedCause: Throwable? get() = cause

    override suspend fun flush() {
        cause?.let { throw it }
        session.sending {
            stage()
            session.flush()
        }
    }

    override suspend fun flushAndClose() {
        if (closed) {
            return
        }

        session.sending {
            stage()
            closed = true
            if (chunked && !discard) {
                session.write(LAST_CHUNK)
            }
        }
    }

    override fun cancel(cause: Throwable?) {
        if (closed) {
            return
        }

        closed = true
        this.cause = cause ?: IOException("Channel was cancelled")
        buffer.clear()
        session.mustClose = true
    }

    // Moves the buffered bytes to the write slab, as one chunk when chunked.
    private fun stage() {
        val size = buffer.size
        if (size == 0L) {
            return
        }

        written += size
        if (contentLength >= 0 && written > contentLength) {
            val e = IllegalStateException("The body is longer than its Content-Length of $contentLength bytes")
            cancel(e)
            throw e
        }

        if (discard) {
            buffer.clear()
            return
        }

        if (chunked) {
            session.write(chunkHeader(size))
        }

        while (!buffer.exhausted()) {
            UnsafeBufferOperations.readFromHead(buffer) { bytes, from, to ->
                session.write(bytes, from, to - from)
                to - from
            }
        }

        if (chunked) {
            session.write(CRLF)
        }
    }

    private companion object {
        val CRLF = "\r\n".encodeToByteArray()
        val LAST_CHUNK = "0\r\n\r\n".encodeToByteArray()

        fun chunkHeader(size: Long): ByteArray = (size.toString(16) + "\r\n").encodeToByteArray()
    }
}
