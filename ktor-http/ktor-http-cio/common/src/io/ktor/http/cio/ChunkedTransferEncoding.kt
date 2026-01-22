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

/**
 * Parse a single HTTP/1.1 chunk-size line as defined by the chunked transfer coding.
 *
 * The input is expected to be positioned at the first byte of the chunk-size line and this
 * function consumes bytes up to and including the terminating CRLF. The returned [Long] is the
 * numeric size of the chunk body in bytes.
 *
 * Parsing rules and state machine:
 * - The chunk size itself is parsed as a hexadecimal number from the beginning of the line up
 *   to (but not including) the first `;` (start of chunk extensions) or CR.
 * - After the first `;` is seen, [inExtension] is set to `true` and all subsequent characters
 *   (including any additional `;`) are treated as part of the extension section and ignored for
 *   size calculation.
 * - Chunk extensions may contain quoted strings (`"..."`). When a `"` is seen, [inQuotes] is
 *   toggled. While [inQuotes] is `true`, every byte other than `"` is skipped without further
  *   interpretation so that extension parameters cannot affect the parsed size.
 * - CR/LF handling is strict: `LF` must be immediately preceded by `CR`. A bare `LF` or `LF`
 *   occurring without a prior `CR` causes an [IOException], preventing acceptance of malformed
 *   or obfuscated input.
 * - The [afterCr] flag tracks whether the last processed byte was a `CR` so that the following
 *   `LF` can be recognized as a valid line terminator.
 *
 * Security and robustness considerations (see KTOR-9263):
 * - Only standard hexadecimal digits are accepted before the first `;`. Any other character in
 *   this region results in an [IOException].
 * - Chunk extensions and their contents are always ignored for the purpose of computing the size,
 *   but are fully consumed from the stream, including quoted strings, to keep the parser in sync.
 * - A hard upper bound of [MAX_CHUNK_SIZE_LENGTH] bytes is enforced for the entire line to
 *   mitigate resource exhaustion and to reject overly long or maliciously crafted chunk-size
 *   headers.
 *
 * This function is intentionally strict and should be modified with care, as relaxing these
 * checks may reintroduce parsing ambiguities or security vulnerabilities.
 */
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
