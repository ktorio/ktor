package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.channels.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteBuffer
import kotlinx.coroutines.experimental.io.packet.*
import kotlinx.io.core.*
import kotlinx.io.core.ByteReadPacket
import java.io.*
import java.io.EOFException
import java.nio.*
import kotlin.coroutines.experimental.*

sealed class MultipartEvent {
    abstract fun release()
    class Preamble(val body: ByteReadPacket) : MultipartEvent() {
        override fun release() {
            body.release()
        }
    }
    class MultipartPart(val headers: Deferred<HttpHeadersMap>, val body: ByteReadChannel) : MultipartEvent() {
        override fun release() {
            headers.invokeOnCompletion { t ->
                if (t != null) {
                    headers.getCompleted().release()
                }
            }
            // TODO body
        }
    }
    class Epilogue(val body: ByteReadPacket) : MultipartEvent() {
        override fun release() {
            body.release()
        }
    }
}

suspend fun copyMultipart(headers: HttpHeadersMap, input: ByteReadChannel, out: ByteWriteChannel) {
    val length = headers["Content-Length"]?.parseDecLong() ?: Long.MAX_VALUE
    input.copyTo(out, length)
}

suspend fun parsePreamble(boundaryPrefixed: ByteBuffer, input: ByteReadChannel, output: BytePacketBuilder, limit: Long = Long.MAX_VALUE): Long {
    return copyUntilBoundary("preamble/prologue", boundaryPrefixed, input, { output.writeFully(it) }, limit)
}

suspend fun parsePart(boundaryPrefixed: ByteBuffer, input: ByteReadChannel, output: ByteWriteChannel, limit: Long = Long.MAX_VALUE): Pair<HttpHeadersMap, Long> {
    val headers = parsePartHeaders(input)
    try {
        val cl = headers["Content-Length"]?.parseDecLong()
        val size = if (cl != null) {
            if (cl > limit) throw IOException("Multipart part content length limit of $limit exceeded (actual size is $cl)")
            input.copyTo(output, cl)
        } else {
            copyUntilBoundary("part", boundaryPrefixed, input, { output.writeFully(it) }, limit)
        }
        output.flush()

        return Pair(headers, size)
    } catch (t: Throwable) {
        headers.release()
        throw t
    }
}

suspend fun boundary(boundaryPrefixed: ByteBuffer, input: ByteReadChannel): Boolean {
    input.skipDelimiter(boundaryPrefixed)

    var result = false
    input.lookAheadSuspend {
        awaitAtLeast(1)
        val buffer = request(0, 1) ?: throw IOException("Failed to pass multipart boundary: unexpected end of stream")
        if (buffer[buffer.position()] != PrefixChar) return@lookAheadSuspend
        if (buffer.remaining() > 1 && buffer[buffer.position() + 1] == PrefixChar) {
            result = true
            consumed(2)
            return@lookAheadSuspend
        }

        awaitAtLeast(2)
        val attempt2buffer = request(1, 1) ?: throw IOException("Failed to pass multipart boundary: unexpected end of stream")
        if (attempt2buffer[attempt2buffer.position()] == PrefixChar) {
            result = true
            consumed(2)
            return@lookAheadSuspend
        }
    }

    return result
}

fun expectMultipart(headers: HttpHeadersMap): Boolean {
    return headers["Content-Type"]?.startsWith("multipart/") ?: false
}

private val headerParameterEndChars = charArrayOf(' ', ';', ',')

fun parseMultipart(coroutineContext: CoroutineContext, input: ByteReadChannel, headers: HttpHeadersMap): ReceiveChannel<MultipartEvent> {
    val contentType = headers["Content-Type"] ?: throw IOException("Failed to parse multipart: no Content-Type header")
    if (!contentType.startsWith("multipart/")) throw IOException("Failed to parse multipart: Content-Type should be multipart/* but it is $contentType")
    val boundaryParameter = contentType.indexOf("boundary=") // TODO parse HTTP header properly instead
    if (boundaryParameter == -1) throw IOException("Failed to parse multipart: Content-Type's boundary parameter is missing")
    val boundaryStart = boundaryParameter + 9
    val boundaryEnd = contentType.indexOfAny(headerParameterEndChars, boundaryStart)
    val boundaryLength = if (boundaryEnd == -1) contentType.length - boundaryStart else boundaryEnd - boundaryStart

    // RFC 2046, sec 5.1.1
    if (boundaryLength > 70) throw IOException("Failed to parse multipart: boundary shouldn't be longer than 70 characters")

    val boundaryBytes: ByteBuffer = ByteBuffer.allocate(boundaryLength + 4)
    val pch = PrefixChar
    boundaryBytes.put(0x0d)
    boundaryBytes.put(0x0a)
    boundaryBytes.put(pch)
    boundaryBytes.put(pch)

    for (i in 0 until boundaryLength) {
        val ch = contentType[boundaryStart + i].toInt() and 0xffff
        if (ch > 0x7f) throw IOException("Failed to parse multipart: wrong boundary byte 0x${ch.toString(16)} - should be 7bit character")
        boundaryBytes.put(ch.toByte())
    }

    boundaryBytes.clear()

    val totalLength = headers["Content-Length"]?.parseDecLong()

    // TODO fail if totalLength = 0 and content subtype is wrong

    return parseMultipart(coroutineContext, boundaryBytes, input, totalLength)
}

private val EmptyCharBuffer = CharBuffer.allocate(0)!!
private val CrLf = ByteBuffer.wrap("\r\n".toByteArray())!!
private val BoundaryTrailingBuffer = ByteBuffer.allocate(8192)!!

fun parseMultipart(coroutineContext: CoroutineContext, boundaryPrefixed: ByteBuffer, input: ByteReadChannel, totalLength: Long?): ReceiveChannel<MultipartEvent> {
    return produce(coroutineContext) {
        val readBeforeParse = input.totalBytesRead
        val firstBoundary = boundaryPrefixed.duplicate()!!.apply {
            position(2)
        }

        val preamble = WritePacket()
        parsePreamble(firstBoundary, input, preamble, 8192)

        if (preamble.size > 0) {
            channel.send(MultipartEvent.Preamble(preamble.build()))
        }

        if (boundary(firstBoundary, input)) {
            return@produce
        }

        val trailingBuffer = BoundaryTrailingBuffer.duplicate()

        do {
            input.readUntilDelimiter(CrLf, trailingBuffer)
            if (input.readUntilDelimiter(CrLf, trailingBuffer) != 0) throw IOException("Failed to parse multipart: boundary line is too long")
            input.skipDelimiter(CrLf)

            val body = ByteChannel()
            val headers = CompletableDeferred<HttpHeadersMap>()
            val part = MultipartEvent.MultipartPart(headers, body)
            channel.send(part)

            val (hh, _) = try {
                parsePart(boundaryPrefixed, input, body)
            } catch (t: Throwable) {
                headers.completeExceptionally(t)
                body.close(t)
                throw t
            }

            headers.complete(hh)
            body.close()
        } while (!boundary(boundaryPrefixed, input))

        if (totalLength != null) {
            val consumedExceptEpilogue = input.totalBytesRead - readBeforeParse
            val size = totalLength - consumedExceptEpilogue
            if (size > Int.MAX_VALUE) throw IOException("Failed to parse multipart: prologue is too long")
            if (size > 0) {
                channel.send(MultipartEvent.Epilogue(input.readPacket(size.toInt())))
            }
        } else {
            // TODO epilogue size?
        }
    }
}

private suspend fun copyUntilBoundary(name: String, boundaryPrefixed: ByteBuffer, input: ByteReadChannel, writeFully: suspend (ByteBuffer) -> Unit, limit: Long = Long.MAX_VALUE): Long {
    val buffer = DefaultByteBufferPool.borrow()
    var copied = 0L

    try {
        while (true) {
            buffer.clear()
            val rc = input.readUntilDelimiter(boundaryPrefixed, buffer)
            if (rc == 0) break // got boundary or eof
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

private suspend fun parsePartHeaders(input: ByteReadChannel): HttpHeadersMap {
    val builder = CharBufferBuilder()

    try {
        return parseHeaders(input, builder, MutableRange(0, 0)) ?: throw EOFException("Failed to parse multipart headers: unexpected end of stream")
    } catch (t: Throwable) {
        builder.release()
        throw t
    }
}

private const val PrefixChar = '-'.toByte()
