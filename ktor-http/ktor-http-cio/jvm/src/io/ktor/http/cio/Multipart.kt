/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.network.util.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import java.io.*
import java.io.EOFException
import java.nio.*
import kotlin.coroutines.*

/**
 * Represents a multipart content starting event. Every part need to be completely consumed or released via [release]
 */
sealed class MultipartEvent {
    /**
     * Release underlying data/packet.
     */
    abstract fun release()

    /**
     * Represents a multipart content preamble. A multipart stream could have at most one preamble.
     * @property body contains preamble's content
     */
    class Preamble(val body: ByteReadPacket) : MultipartEvent() {
        override fun release() {
            body.release()
        }
    }

    /**
     * Represents a multipart part. There could be any number of parts in a multipart stream. Please note that
     * it is important to consume [body] otherwise multipart parser could get stuck (suspend)
     * so you will not receive more events.
     *
     * @property headers deferred that will be completed once will be parsed
     * @property body a channel of part content
     */
    class MultipartPart(val headers: Deferred<HttpHeadersMap>, val body: ByteReadChannel) : MultipartEvent() {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun release() {
            headers.invokeOnCompletion { t ->
                if (t != null) {
                    headers.getCompleted().release()
                }
            }
            runBlocking {
                body.discard()
            }
        }
    }

    /**
     * Represents a multipart content epilogue. A multipart stream could have at most one epilogue.
     * @property body contains epilogue's content
     */
    class Epilogue(val body: ByteReadPacket) : MultipartEvent() {
        override fun release() {
            body.release()
        }
    }
}

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated(
    "Simply copy required number of bytes from input to output instead",
    level = DeprecationLevel.HIDDEN
)
suspend fun copyMultipart(headers: HttpHeadersMap, input: ByteReadChannel, out: ByteWriteChannel) {
    val length = headers["Content-Length"]?.parseDecLong() ?: Long.MAX_VALUE
    input.copyTo(out, length)
}

/**
 * Parse a multipart preamble
 * @return number of bytes copied
 */
@Deprecated("This is going to be removed. Use parseMultipart instead.")
suspend fun parsePreamble(
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel,
    output: BytePacketBuilder,
    limit: Long = Long.MAX_VALUE
): Long {
    return parsePreambleImpl(boundaryPrefixed, input, output, limit)
}

/**
 * Parse a multipart preamble
 * @return number of bytes copied
 */
private suspend fun parsePreambleImpl(
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel,
    output: BytePacketBuilder,
    limit: Long = Long.MAX_VALUE
): Long {
    return copyUntilBoundary(
        "preamble/prologue",
        boundaryPrefixed,
        input,
        { output.writeFully(it) },
        limit
    )
}

/**
 * Parse multipart part headers and body. Body bytes will be copied to [output] but up to [limit] bytes
 */
@Deprecated("This is going to be removed. Use parseMultipart instead.")
suspend fun parsePart(
    boundaryPrefixed: ByteBuffer, input: ByteReadChannel, output: ByteWriteChannel,
    limit: Long = Long.MAX_VALUE
): Pair<HttpHeadersMap, Long> {
    val headers = parsePartHeadersImpl(input)
    try {
        val size = parsePartBodyImpl(boundaryPrefixed, input, output, headers, limit)
        return Pair(headers, size)
    } catch (t: Throwable) {
        headers.release()
        throw t
    }
}

/**
 * Parse multipart part headers
 */
@Deprecated("This is going to be removed. Use parseMultipart instead.")
suspend fun parsePartHeaders(input: ByteReadChannel): HttpHeadersMap {
    return parsePartHeadersImpl(input)
}

/**
 * Parse multipart part headers
 */
private suspend fun parsePartHeadersImpl(input: ByteReadChannel): HttpHeadersMap {
    val builder = CharArrayBuilder()

    try {
        return parseHeaders(input, builder)
            ?: throw EOFException("Failed to parse multipart headers: unexpected end of stream")
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

/**
 * Parse multipart part body copying them to [output] channel but up to [limit] bytes
 */
@Deprecated("This is going to be removed. Use parseMultipart instead.")
suspend fun parsePartBody(
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel, output: ByteWriteChannel,
    headers: HttpHeadersMap, limit: Long = Long.MAX_VALUE
): Long {
    return parsePartBodyImpl(boundaryPrefixed, input, output, headers, limit)
}

/**
 * Parse multipart part body copying them to [output] channel but up to [limit] bytes
 */
private suspend fun parsePartBodyImpl(
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel, output: ByteWriteChannel,
    headers: HttpHeadersMap, limit: Long = Long.MAX_VALUE
): Long {
    val cl = headers["Content-Length"]?.parseDecLong()
    val size = if (cl != null) {
        if (cl > limit) throw IOException("Multipart part content length limit of $limit exceeded (actual size is $cl)")
        input.copyTo(output, cl)
    } else {
        copyUntilBoundary("part", boundaryPrefixed, input, { output.writeFully(it) }, limit)
    }
    output.flush()

    return size
}

/**
 * Skip multipart boundary
 */
@OptIn(ExperimentalIoApi::class)
@Deprecated("This is going to be removed. Use parseMultipart instead.")
suspend fun boundary(boundaryPrefixed: ByteBuffer, input: ByteReadChannel): Boolean {
    return skipBoundary(boundaryPrefixed, input)
}

/**
 * Skip multipart boundary
 */
private suspend fun skipBoundary(boundaryPrefixed: ByteBuffer, input: ByteReadChannel): Boolean {
    input.skipDelimiter(boundaryPrefixed)

    var result = false
    @Suppress("DEPRECATION")
    input.lookAheadSuspend {
        awaitAtLeast(1)
        val buffer = request(0, 1)
            ?: throw IOException("Failed to pass multipart boundary: unexpected end of stream")

        if (buffer[buffer.position()] != PrefixChar) return@lookAheadSuspend
        if (buffer.remaining() > 1 && buffer[buffer.position() + 1] == PrefixChar) {
            result = true
            consumed(2)
            return@lookAheadSuspend
        }

        awaitAtLeast(2)
        val attempt2buffer = request(1, 1)
            ?: throw IOException("Failed to pass multipart boundary: unexpected end of stream")

        if (attempt2buffer[attempt2buffer.position()] == PrefixChar) {
            result = true
            consumed(2)
            return@lookAheadSuspend
        }
    }

    return result
}

/**
 * Check if we have multipart content
 */
@Deprecated("This is going to be removed.")
fun expectMultipart(headers: HttpHeadersMap): Boolean {
    return headers["Content-Type"]?.startsWith("multipart/") ?: false
}

@Suppress("KDocMissingDocumentation", "unused", "DeprecatedCallableAddReplaceWith")
@Deprecated("Specify CoroutineScope explicitly", level = DeprecationLevel.HIDDEN)
fun parseMultipart(
    coroutineContext: CoroutineContext,
    input: ByteReadChannel,
    headers: HttpHeadersMap
): ReceiveChannel<MultipartEvent> {
    return CoroutineScope(coroutineContext).parseMultipart(input, headers)
}


/**
 * Starts a multipart parser coroutine producing multipart events
 */
@KtorExperimentalAPI
fun CoroutineScope.parseMultipart(input: ByteReadChannel, headers: HttpHeadersMap): ReceiveChannel<MultipartEvent> {
    val contentType = headers["Content-Type"] ?: throw IOException("Failed to parse multipart: no Content-Type header")
    val contentLength = headers["Content-Length"]?.parseDecLong()

    return parseMultipart(input, contentType, contentLength)
}

@Suppress("KDocMissingDocumentation", "unused", "DeprecatedCallableAddReplaceWith")
@Deprecated("Specify coroutine scope explicitly", level = DeprecationLevel.HIDDEN)
fun parseMultipart(
    coroutineContext: CoroutineContext,
    input: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?
): ReceiveChannel<MultipartEvent> {
    return CoroutineScope(coroutineContext).parseMultipart(input, contentType, contentLength)
}

/**
 * Starts a multipart parser coroutine producing multipart events
 */
@KtorExperimentalAPI
fun CoroutineScope.parseMultipart(
    input: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?
): ReceiveChannel<MultipartEvent> {
    if (!contentType.startsWith("multipart/")) throw IOException("Failed to parse multipart: Content-Type should be multipart/* but it is $contentType")
    val boundaryBytes = parseBoundary(contentType)

    // TODO fail if contentLength = 0 and content subtype is wrong

    @Suppress("DEPRECATION")
    return parseMultipart(boundaryBytes, input, contentLength)
}

private val CrLf = ByteBuffer.wrap("\r\n".toByteArray())!!
private val BoundaryTrailingBuffer = ByteBuffer.allocate(8192)!!

@Suppress("KDocMissingDocumentation", "unused", "DeprecatedCallableAddReplaceWith")
@Deprecated("Use parseMultipart with coroutine scope specified", level = DeprecationLevel.HIDDEN)
fun parseMultipart(
    coroutineContext: CoroutineContext,
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel,
    totalLength: Long?
): ReceiveChannel<MultipartEvent> {
    @Suppress("DEPRECATION")
    return CoroutineScope(coroutineContext).parseMultipart(boundaryPrefixed, input, totalLength)
}

/**
 * Starts a multipart parser coroutine producing multipart events
 */
@Deprecated("This is going to be removed. Use parseMultipart(contentType) instead.")
fun CoroutineScope.parseMultipart(
    boundaryPrefixed: ByteBuffer, input: ByteReadChannel, totalLength: Long?
): ReceiveChannel<MultipartEvent> = produce {
    @Suppress("DEPRECATION")
    val readBeforeParse = input.totalBytesRead
    val firstBoundary = boundaryPrefixed.duplicate()!!.apply {
        position(2)
    }

    val preamble = BytePacketBuilder()
    parsePreambleImpl(firstBoundary, input, preamble, 8192)

    if (preamble.size > 0) {
        send(MultipartEvent.Preamble(preamble.build()))
    }

    if (skipBoundary(firstBoundary, input)) {
        return@produce
    }

    val trailingBuffer = BoundaryTrailingBuffer.duplicate()

    do {
        input.readUntilDelimiter(CrLf, trailingBuffer)
        if (input.readUntilDelimiter(
                CrLf,
                trailingBuffer
            ) != 0
        ) throw IOException("Failed to parse multipart: boundary line is too long")
        input.skipDelimiter(CrLf)

        val body = ByteChannel()
        val headers = CompletableDeferred<HttpHeadersMap>()
        val part = MultipartEvent.MultipartPart(headers, body)
        send(part)

        var hh: HttpHeadersMap? = null
        try {
            hh = parsePartHeadersImpl(input)
            if (!headers.complete(hh)) {
                hh.release()
                throw CancellationException("Multipart processing has been cancelled")
            }
            parsePartBodyImpl(boundaryPrefixed, input, body, hh)
        } catch (t: Throwable) {
            if (headers.completeExceptionally(t)) {
                hh?.release()
            }
            body.close(t)
            throw t
        }

        body.close()
    } while (!skipBoundary(boundaryPrefixed, input))

    if (totalLength != null) {
        @Suppress("DEPRECATION")
        val consumedExceptEpilogue = input.totalBytesRead - readBeforeParse
        val size = totalLength - consumedExceptEpilogue
        if (size > Int.MAX_VALUE) throw IOException("Failed to parse multipart: prologue is too long")
        if (size > 0) {
            send(MultipartEvent.Epilogue(input.readPacket(size.toInt())))
        }
    } else {
        val epilogueContent = input.readRemaining()
        if (epilogueContent.isNotEmpty) {
            send(MultipartEvent.Epilogue(epilogueContent))
        }
    }
}

/**
 * @return number of copied bytes or 0 if a boundary of EOF encountered
 */
private suspend fun copyUntilBoundary(
    name: String,
    boundaryPrefixed: ByteBuffer,
    input: ByteReadChannel,
    writeFully: suspend (ByteBuffer) -> Unit,
    limit: Long = Long.MAX_VALUE
): Long {
    val buffer = DefaultByteBufferPool.borrow()
    var copied = 0L

    try {
        while (true) {
            buffer.clear()
            val rc = input.readUntilDelimiter(boundaryPrefixed, buffer)
            if (rc <= 0) break // got boundary or eof
            buffer.flip()
            writeFully(buffer)
            copied += rc
            if (copied > limit) {
                throw IOException("Multipart $name limit of $limit bytes exceeded")
            }
        }

        return copied
    } finally {
        DefaultByteBufferPool.recycle(buffer)
    }
}

private const val PrefixChar = '-'.toByte()

private fun findBoundary(contentType: CharSequence): Int {
    var state = 0 // 0 header value, 1 param name, 2 param value unquoted, 3 param value quoted, 4 escaped
    var paramNameCount = 0

    for (i in contentType.indices) {
        val ch = contentType[i]

        when (state) {
            0 -> {
                if (ch == ';') {
                    state = 1
                    paramNameCount = 0
                }
            }
            1 -> {
                if (ch == '=') {
                    state = 2
                } else if (ch == ';') {
                    // do nothing
                    paramNameCount = 0
                } else if (ch == ',') {
                    state = 0
                } else if (ch == ' ') {
                    // do nothing
                } else if (paramNameCount == 0 && contentType.startsWith("boundary=", i, ignoreCase = true)) {
                    return i
                } else {
                    paramNameCount++
                }
            }
            2 -> {
                when (ch) {
                    '"' -> state = 3
                    ',' -> state = 0
                    ';' -> {
                        state = 1
                        paramNameCount = 0
                    }
                }
            }
            3 -> {
                if (ch == '"') {
                    state = 1
                    paramNameCount = 0
                } else if (ch == '\\') {
                    state = 4
                }
            }
            4 -> {
                state = 3
            }
        }
    }

    return -1
}

/**
 * Parse multipart boundary encoded in [contentType] header value
 * @return a buffer containing CRLF, prefix '--' and boundary bytes
 */
@KtorExperimentalAPI
fun parseBoundary(contentType: CharSequence): ByteBuffer {
    val boundaryParameter = findBoundary(contentType)

    if (boundaryParameter == -1) throw IOException("Failed to parse multipart: Content-Type's boundary parameter is missing")
    val boundaryStart = boundaryParameter + 9

    val boundaryBytes: ByteBuffer = ByteBuffer.allocate(74)
    boundaryBytes.put(0x0d)
    boundaryBytes.put(0x0a)
    boundaryBytes.put(PrefixChar)
    boundaryBytes.put(PrefixChar)

    var state = 0 // 0 - skipping spaces, 1 - unquoted characters, 2 - quoted no escape, 3 - quoted after escape

    loop@ for (i in boundaryStart until contentType.length) {
        val ch = contentType[i]
        val v = ch.toInt() and 0xffff
        if (v and 0xffff > 0x7f) throw IOException("Failed to parse multipart: wrong boundary byte 0x${v.toString(16)} - should be 7bit character")

        when (state) {
            0 -> {
                when (ch) {
                    ' ' -> {
                        // skip space
                    }
                    '"' -> {
                        state = 2 // start quoted string parsing
                    }
                    ';', ',' -> {
                        break@loop
                    }
                    else -> {
                        state = 1
                        boundaryBytes.put(v.toByte())
                    }
                }
            }
            1 -> { // non-quoted string
                if (ch == ' ' || ch == ',' || ch == ';') { // space, comma or semicolon (;)
                    break@loop
                } else if (boundaryBytes.hasRemaining()) {
                    boundaryBytes.put(v.toByte())
                } else {
                    //  RFC 2046, sec 5.1.1
                    throw IOException("Failed to parse multipart: boundary shouldn't be longer than 70 characters")
                }
            }
            2 -> {
                if (ch == '\\') {
                    state = 3
                } else if (ch == '"') {
                    break@loop
                } else if (boundaryBytes.hasRemaining()) {
                    boundaryBytes.put(v.toByte())
                } else {
                    //  RFC 2046, sec 5.1.1
                    throw IOException("Failed to parse multipart: boundary shouldn't be longer than 70 characters")
                }
            }
            3 -> {
                if (boundaryBytes.hasRemaining()) {
                    boundaryBytes.put(v.toByte())
                    state = 2
                } else {
                    //  RFC 2046, sec 5.1.1
                    throw IOException("Failed to parse multipart: boundary shouldn't be longer than 70 characters")
                }
            }
        }
    }

    boundaryBytes.flip()

    if (boundaryBytes.remaining() == 4) {
        throw IOException("Empty multipart boundary is not allowed")
    }

    return boundaryBytes
}
