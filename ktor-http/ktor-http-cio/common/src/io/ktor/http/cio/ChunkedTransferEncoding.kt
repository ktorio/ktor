/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.http.cio

import io.ktor.http.cio.internals.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext

private const val MAX_CHUNK_SIZE_LENGTH = 128

/**
 * Decoder job type
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.DecoderJob)
 */
public typealias DecoderJob = WriterJob

/**
 * Start a chunked stream decoder coroutine
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.decodeChunked)
 */
@Deprecated(
    "Specify content length if known or pass -1L",
    ReplaceWith("decodeChunked(input, -1L)"),
    level = DeprecationLevel.ERROR
)
public fun CoroutineScope.decodeChunked(input: ByteReadChannel): DecoderJob =
    decodeChunked(input, -1L)

/**
 * Start a chunked stream decoder coroutine
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.decodeChunked)
 */
@Suppress("UNUSED_PARAMETER")
public fun CoroutineScope.decodeChunked(input: ByteReadChannel, contentLength: Long): DecoderJob =
    writer(coroutineContext) {
        decodeChunked(input, channel)
    }

/**
 * Decode chunked transfer encoding from the [input] channel and write the result in [out].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.decodeChunked)
 *
 * @throws EOFException if stream has ended unexpectedly.
 * @throws ParserException if the format is invalid.
 */
@OptIn(InternalAPI::class)
public suspend fun decodeChunked(input: ByteReadChannel, out: ByteWriteChannel) {
    var totalBytesCopied = 0L

    try {
        while (!input.exhausted()) {
            val chunkSize = parseChunkSize(input)
            if (chunkSize == 0L) {
                input.skipCrLf()
                break
            }

            input.copyTo(out, chunkSize)
            input.skipCrLf()
            out.flush()
            totalBytesCopied += chunkSize
        }
    } catch (t: Throwable) {
        out.close(t)
        throw t
    } finally {
        out.flushAndClose()
    }
}

private suspend fun ByteReadChannel.skipCrLf() {
    when (val b = readByte()) {
        CR -> if (readByte() != LF) throw IOException("Expected LF")
        LF -> return
        else -> throw IOException("Expected CRLF but found 0x${b.toString(16)}")
    }
}

private const val CR = '\r'.code.toByte()
private const val LF = '\n'.code.toByte()
private const val SEMICOLON = ';'.code.toByte()
private const val QUOTE = '"'.code.toByte()

@OptIn(InternalAPI::class)
private suspend fun parseChunkSize(input: ByteReadChannel): Long {
    var result = 0L
    var inExtension = false
    var inQuotes = false
    var afterCr = false
    var i = 0
    while (i++ < MAX_CHUNK_SIZE_LENGTH) {
        val byte = input.readByte()
        if (inQuotes && byte != QUOTE) continue
        try {
            when (byte) {
                CR -> continue
                LF -> {
                    if (!afterCr) {
                        throw IOException("Illegal newline character in chunk size")
                    }
                    return result
                }
                QUOTE -> inQuotes = !inQuotes
                SEMICOLON -> inExtension = true
                else -> {
                    if (inExtension) continue // always ignore extensions
                    val intValue = byte.toInt() and 0xffff
                    val digit = if (intValue < 0xff) HexTable[intValue] else -1L
                    if (digit == -1L) throw IOException("Invalid chunk size character: 0x${intValue.toString(16)}")
                    result = (result shl 4) or digit
                }
            }
        } finally {
            afterCr = byte == CR
        }
    }
    throw IOException("Chunk size limit exceeded")
}

/**
 * Encoder job type
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.EncoderJob)
 */
public typealias EncoderJob = ReaderJob

/**
 * Start chunked stream encoding coroutine
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.encodeChunked)
 */
@OptIn(DelicateCoroutinesApi::class)
public fun encodeChunked(
    output: ByteWriteChannel,
    coroutineContext: CoroutineContext
): EncoderJob = GlobalScope.reader(coroutineContext, autoFlush = false) {
    encodeChunked(output, channel)
}

/**
 * Chunked stream encoding loop
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.http.cio.encodeChunked)
 */
public suspend fun encodeChunked(output: ByteWriteChannel, input: ByteReadChannel) {
    try {
        while (!input.isClosedForRead) {
            input.read { source, startIndex, endIndex ->
                if (endIndex == startIndex) return@read 0
                output.writeChunk(source, startIndex, endIndex)
            }
        }

        input.rethrowCloseCause()
        output.writeFully(LastChunkBytes)
    } catch (cause: Throwable) {
        output.close(cause)
        input.cancel(cause)
        throw cause
    } finally {
        output.flush()
    }
}

private fun ByteReadChannel.rethrowCloseCause() {
    val cause = when (this) {
        is ByteChannel -> closedCause
        else -> null
    }
    if (cause != null) throw cause
}

private const val CrLfShort: Short = 0x0d0a
private val CrLf = "\r\n".toByteArray()
private val LastChunkBytes = "0\r\n\r\n".toByteArray()

private suspend fun ByteWriteChannel.writeChunk(memory: ByteArray, startIndex: Int, endIndex: Int): Int {
    val size = endIndex - startIndex
    writeIntHex(size)
    writeShort(CrLfShort)

    writeFully(memory, startIndex, endIndex)
    writeFully(CrLf)
    flush()

    return size
}
