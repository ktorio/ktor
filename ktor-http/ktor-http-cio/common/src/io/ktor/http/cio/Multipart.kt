/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.*
import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.produce
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlinx.io.Source
import kotlinx.io.bytestring.ByteString

/**
 * Represents a multipart content starting event. Every part need to be completely consumed or released via [release]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.MultipartEvent)
 */

public sealed class MultipartEvent {
    /**
     * Release underlying data/packet.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.MultipartEvent.release)
     */
    public abstract fun release()

    /**
     * Represents a multipart content preamble. A multipart stream could have at most one preamble.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.MultipartEvent.Preamble)
     *
     * @property body contains preamble's content
     */
    public class Preamble(
        public val body: Source
    ) : MultipartEvent() {
        override fun release() {
            body.close()
        }
    }

    /**
     * Represents a multipart part. There could be any number of parts in a multipart stream. Please note that
     * it is important to consume [body] otherwise multipart parser could get stuck (suspend)
     * so you will not receive more events.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.MultipartEvent.MultipartPart)
     *
     * @property headers deferred that will be completed once will be parsed
     * @property body a channel of part content
     */
    public class MultipartPart(
        public val headers: Deferred<HttpHeadersMap>,
        public val body: ByteReadChannel
    ) : MultipartEvent() {
        @OptIn(ExperimentalCoroutinesApi::class)
        override fun release() {
            headers.invokeOnCompletion { t ->
                if (t != null) {
                    headers.getCompleted().release()
                }
            }

            body.discardBlocking()
        }
    }

    /**
     * Represents a multipart content epilogue. A multipart stream could have at most one epilogue.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.MultipartEvent.Epilogue)
     *
     * @property body contains epilogue's content
     */
    public class Epilogue(
        public val body: Source
    ) : MultipartEvent() {
        override fun release() {
            body.close()
        }
    }
}

internal expect fun ByteReadChannel.discardBlocking()

/**
 * Parse a multipart preamble
 * @return number of bytes copied
 */
private suspend fun parsePreambleImpl(
    boundary: ByteString,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    limit: Long = Long.MAX_VALUE
): Long = input.readUntil(boundary, output, limit, ignoreMissing = true)

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
private suspend fun parsePartBodyImpl(
    boundaryPrefixed: ByteString,
    input: ByteReadChannel,
    output: ByteWriteChannel,
    headers: HttpHeadersMap,
    limit: Long,
): Long {
    val byteCount = when (val contentLength = headers["Content-Length"]?.parseDecLong()) {
        null -> input.readUntil(boundaryPrefixed, output, limit, ignoreMissing = true)
        in 0L..limit -> input.copyTo(output, contentLength) + input.skipIfFoundReadCount(boundaryPrefixed)
        else -> throwLimitExceeded(contentLength, limit)
    }
    output.flush()

    return byteCount
}

// Returns the size of the prefix if skipped
private suspend fun ByteReadChannel.skipIfFoundReadCount(prefix: ByteString): Long =
    if (skipIfFound(prefix)) {
        prefix.size.toLong()
    } else {
        0L
    }

/**
 * Starts a multipart parser coroutine producing multipart events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.parseMultipart)
 */
@OptIn(InternalAPI::class)
public fun CoroutineScope.parseMultipart(
    input: ByteReadChannel,
    headers: HttpHeadersMap,
    maxPartSize: Long = Long.MAX_VALUE
): ReceiveChannel<MultipartEvent> {
    val contentType = headers["Content-Type"] ?: throw UnsupportedMediaTypeExceptionCIO(
        "Failed to parse multipart: no Content-Type header"
    )
    val contentLength = headers["Content-Length"]?.parseDecLong()

    return parseMultipart(input, contentType, contentLength, maxPartSize)
}

/**
 * Starts a multipart parser coroutine producing multipart events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.parseMultipart)
 */
@OptIn(InternalAPI::class)
public fun CoroutineScope.parseMultipart(
    input: ByteReadChannel,
    contentType: CharSequence,
    contentLength: Long?,
    maxPartSize: Long = Long.MAX_VALUE,
): ReceiveChannel<MultipartEvent> {
    if (contentType !in ContentType.MultiPart) {
        throw UnsupportedMediaTypeExceptionCIO(
            "Failed to parse multipart: Content-Type should be multipart/* but it is $contentType"
        )
    }
    val boundaryByteBuffer = parseBoundaryInternal(contentType)
    val boundaryBytes = ByteString(boundaryByteBuffer)

    // TODO fail if contentLength = 0 and content subtype is wrong
    return parseMultipart(boundaryBytes, input, contentLength, maxPartSize)
}

private val CrLf = ByteString("\r\n".toByteArray())

@OptIn(ExperimentalCoroutinesApi::class)
private fun CoroutineScope.parseMultipart(
    boundaryPrefixed: ByteString,
    input: ByteReadChannel,
    totalLength: Long?,
    maxPartSize: Long
): ReceiveChannel<MultipartEvent> = produce {
    val countedInput = input.counted()
    val readBeforeParse = countedInput.totalBytesRead
    val firstBoundary = boundaryPrefixed.substring(PrefixString.size)

    val preambleData = writer {
        parsePreambleImpl(firstBoundary, countedInput, channel, 8193)
        channel.flushAndClose()
    }.channel.readRemaining()

    if (preambleData.remaining > 0L) {
        send(MultipartEvent.Preamble(preambleData))
    }

    while (!countedInput.isClosedForRead && !countedInput.skipIfFound(PrefixString)) {
        countedInput.skipIfFound(CrLf)

        val body = ByteChannel()
        val headers = CompletableDeferred<HttpHeadersMap>()
        val part = MultipartEvent.MultipartPart(headers, body)
        send(part)

        var headersMap: HttpHeadersMap? = null
        try {
            headersMap = parsePartHeadersImpl(countedInput)
            if (!headers.complete(headersMap)) {
                headersMap.release()
                throw kotlin.coroutines.cancellation.CancellationException(
                    "Multipart processing has been cancelled"
                )
            }
            parsePartBodyImpl(boundaryPrefixed, countedInput, body, headersMap, maxPartSize)
            body.close()
        } catch (cause: Throwable) {
            if (headers.completeExceptionally(cause)) {
                headersMap?.release()
            }
            body.close(cause)
            throw cause
        }
    }

    // Can be followed by two carriage returns
    countedInput.skipIfFound(CrLf)
    countedInput.skipIfFound(CrLf)

    if (totalLength != null) {
        val consumedExceptEpilogue = countedInput.totalBytesRead - readBeforeParse
        val size = totalLength - consumedExceptEpilogue
        if (size > Int.MAX_VALUE) throw IOException("Failed to parse multipart: prologue is too long")
        if (size > 0) {
            send(MultipartEvent.Epilogue(countedInput.readPacket(size.toInt())))
        }
    } else {
        val epilogueContent = countedInput.readRemaining()
        if (!epilogueContent.exhausted()) {
            send(MultipartEvent.Epilogue(epilogueContent))
        }
    }
}

private const val PrefixChar = '-'.code.toByte()
private val PrefixString = ByteString(PrefixChar, PrefixChar)

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
internal fun parseBoundaryInternal(contentType: CharSequence): ByteArray {
    val boundaryParameter = findBoundary(contentType)

    if (boundaryParameter == -1) {
        throw IOException("Failed to parse multipart: Content-Type's boundary parameter is missing")
    }
    val boundaryStart = boundaryParameter + 9

    val boundaryBytes = ByteArray(74)
    var position = 0

    fun put(value: Byte) {
        if (position >= boundaryBytes.size) {
            throw IOException(
                "Failed to parse multipart: boundary shouldn't be longer than 70 characters"
            )
        }
        boundaryBytes[position++] = value
    }

    put(0x0d)
    put(0x0a)
    put(PrefixChar)
    put(PrefixChar)

    var state = 0 // 0 - skipping spaces, 1 - unquoted characters, 2 - quoted no escape, 3 - quoted after escape

    loop@ for (i in boundaryStart until contentType.length) {
        val ch = contentType[i]
        val v = ch.code and 0xffff
        if (v and 0xffff > 0x7f) {
            throw IOException(
                "Failed to parse multipart: wrong boundary byte 0x${v.toString(16)} - should be 7bit character"
            )
        }

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
                        put(v.toByte())
                    }
                }
            }
            1 -> { // non-quoted string
                if (ch == ' ' || ch == ',' || ch == ';') { // space, comma or semicolon (;)
                    break@loop
                } else {
                    put(v.toByte())
                }
            }

            2 -> {
                if (ch == '\\') {
                    state = 3
                } else if (ch == '"') {
                    break@loop
                } else {
                    put(v.toByte())
                }
            }
            3 -> {
                put(v.toByte())
                state = 2
            }
        }
    }

    if (position == 4) {
        throw IOException("Empty multipart boundary is not allowed")
    }

    return boundaryBytes.copyOfRange(0, position)
}

private fun throwLimitExceeded(actual: Long, limit: Long): Nothing =
    throw IOException(
        "Multipart content length exceeds limit $actual > $limit; " +
            "limit is defined using 'formFieldLimit' argument"
    )
