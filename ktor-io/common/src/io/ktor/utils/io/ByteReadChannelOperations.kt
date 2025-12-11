/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.unsafe.UnsafeBufferOperations
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.min

@OptIn(InternalAPI::class)
public val ByteWriteChannel.availableForWrite: Int
    get() = CHANNEL_MAX_SIZE - writeBuffer.size

/**
 * Suspends the channel until it is exhausted or gets closed.
 * If the read buffer is empty, it suspends until there are bytes available in the channel.
 * Once the channel is exhausted or closed, this function returns.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.exhausted)
 *
 * @return `true` if the channel is exhausted, `false` if EOF is reached or an error occurred.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.exhausted(): Boolean {
    if (readBuffer.exhausted()) awaitContent()
    return readBuffer.exhausted()
}

public suspend fun ByteReadChannel.toByteArray(): ByteArray {
    return readBuffer().readBytes()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readByte(): Byte {
    val currentBuffer = readBuffer
    if (currentBuffer.exhausted() && !awaitContent()) {
        throw EOFException("Not enough data available")
    }
    return currentBuffer.readByte()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readShort(): Short {
    awaitUntilReadable(Short.SIZE_BYTES)
    return readBuffer.readShort()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readInt(): Int {
    awaitUntilReadable(Int.SIZE_BYTES)
    return readBuffer.readInt()
}

/**
 * Reads a 32-bit floating-point value from the current [ByteReadChannel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readFloat)
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFloat(): Float {
    awaitUntilReadable(Float.SIZE_BYTES)
    return readBuffer.readFloat()
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readLong(): Long {
    awaitUntilReadable(Long.SIZE_BYTES)
    return readBuffer.readLong()
}

/**
 * Reads a 32-bit floating-point value from the current [ByteReadChannel].
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readDouble)
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readDouble(): Double {
    awaitUntilReadable(Double.SIZE_BYTES)
    return readBuffer.readDouble()
}

private suspend fun ByteReadChannel.awaitUntilReadable(numberOfBytes: Int) {
    if (!awaitContent(numberOfBytes)) {
        throw EOFException("Not enough data available")
    }
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readBuffer(): Buffer {
    val result = Buffer()
    while (!isClosedForRead) {
        result.transferFrom(readBuffer)
        awaitContent()
    }

    closedCause?.let { throw it }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readBuffer(max: Int): Buffer {
    val result = Buffer()
    var remaining = max

    while (remaining > 0 && !isClosedForRead) {
        if (readBuffer.exhausted()) awaitContent()

        val size = minOf(remaining.toLong(), readBuffer.remaining)
        readBuffer.readTo(result, size)
        remaining -= size.toInt()
    }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyAndClose(channel: ByteWriteChannel): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            channel.flush()
            awaitContent()
        }

        closedCause?.let { throw it }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flushAndClose()
    }

    return result
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyTo(channel: ByteWriteChannel): Long {
    var result = 0L
    try {
        while (!isClosedForRead) {
            result += readBuffer.transferTo(channel.writeBuffer)
            channel.flush()
            awaitContent()
        }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flush()
    }

    return result
}

/**
 * Reads bytes from this [ByteReadChannel] and writes them to the specified [sink].
 * If an exception is thrown, the channel and sink are closed.
 *
 * @param sink the destination to write bytes to
 * @param limit the maximum number of bytes to transfer, defaults to [Long.MAX_VALUE]
 * @return the number of bytes transferred
 * @throws IOException if the channel is closed with an error
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readTo(sink: RawSink, limit: Long = Long.MAX_VALUE): Long {
    var remaining = limit
    try {
        while (!isClosedForRead && remaining > 0) {
            if (readBuffer.exhausted()) awaitContent()
            val byteCount = minOf(remaining, readBuffer.remaining)
            readBuffer.readTo(sink, byteCount)
            remaining -= byteCount
            sink.flush()
        }
    } catch (cause: Throwable) {
        cancel(cause)
        sink.close()
        throw cause
    }

    rethrowCloseCauseIfNeeded()
    return limit - remaining
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.copyTo(channel: ByteWriteChannel, limit: Long): Long {
    var remaining = limit
    try {
        while (!isClosedForRead && remaining > 0) {
            if (readBuffer.exhausted()) awaitContent()
            val count = minOf(remaining, readBuffer.remaining)
            readBuffer.readTo(channel.writeBuffer, count)
            remaining -= count
            channel.flush()
        }
    } catch (cause: Throwable) {
        cancel(cause)
        channel.close(cause)
        throw cause
    } finally {
        channel.flush()
    }

    return limit - remaining
}

public suspend fun ByteReadChannel.readByteArray(count: Int): ByteArray = buildPacket {
    while (size < count) {
        val packet = readPacket(count - size)
        writePacket(packet)
    }
}.readByteArray()

@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readRemaining(): Source {
    val result = BytePacketBuilder()
    while (!isClosedForRead) {
        result.transferFrom(readBuffer)
        awaitContent()
    }

    rethrowCloseCauseIfNeeded()
    return result.buffer
}

@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readRemaining(max: Long): Source {
    val result = BytePacketBuilder()
    var remaining = max
    while (!isClosedForRead && remaining > 0) {
        if (remaining >= readBuffer.remaining) {
            remaining -= readBuffer.remaining
            readBuffer.transferTo(result)
        } else {
            readBuffer.readTo(result, remaining)
            remaining = 0
        }

        awaitContent()
    }

    return result.buffer
}

/**
 * Reads all available bytes to [buffer] and returns immediately or suspends if no bytes available
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readAvailable)
 *
 * @return number of bytes were read or `-1` if the channel has been closed
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readAvailable(
    buffer: ByteArray,
    offset: Int = 0,
    length: Int = buffer.size - offset
): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    return readBuffer.readAvailable(buffer, offset, length)
}

/**
 * Invokes [block] if it is possible to read at least [min] byte
 * providing buffer to it so lambda can read from the buffer
 * up to [Buffer.remaining] bytes. If there are no [min] bytes available then the invocation returns -1.
 *
 * Warning: it is not guaranteed that all of available bytes will be represented as a single byte buffer
 * eg: it could be 4 bytes available for read but the provided byte buffer could have only 2 available bytes:
 * in this case you have to invoke read again (with decreased [min] accordingly).
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readAvailable)
 *
 * @param min amount of bytes available for read, should be positive
 * @param block to be invoked when at least [min] bytes available
 *
 * @return number of consumed bytes or -1 if the block wasn't executed.
 */
@OptIn(InternalAPI::class, InternalIoApi::class)
public fun ByteReadChannel.readAvailable(min: Int, block: (Buffer) -> Int): Int {
    require(min > 0) { "min should be positive" }
    require(min <= CHANNEL_MAX_SIZE) { "Min($min) shouldn't be greater than $CHANNEL_MAX_SIZE" }

    if (availableForRead < min) return -1
    return block(readBuffer.buffer)
}

public class ReaderScope(
    public val channel: ByteReadChannel,
    override val coroutineContext: CoroutineContext
) : CoroutineScope

public class ReaderJob internal constructor(
    public val channel: ByteWriteChannel,
    public override val job: Job
) : ChannelJob {
    /**
     * To avoid the risk of concurrent write operations, we cancel the nested job before
     * performing `flushAndClose` on the [ByteWriteChannel].
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.ReaderJob.flushAndClose)
     */
    @InternalAPI
    public suspend fun flushAndClose() {
        job.cancelChildren()
        job.children.forEach {
            it.cancel() // Children may appear at this point so we cancel them before joining
            it.join()
        }
        channel.flushAndClose()
    }
}

@Suppress("UNUSED_PARAMETER")
public fun CoroutineScope.reader(
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
    autoFlush: Boolean = false,
    block: suspend ReaderScope.() -> Unit
): ReaderJob = reader(coroutineContext, ByteChannel(), block)

@OptIn(InternalCoroutinesApi::class)
public fun CoroutineScope.reader(
    coroutineContext: CoroutineContext,
    channel: ByteChannel,
    block: suspend ReaderScope.() -> Unit
): ReaderJob {
    val job = launch(coroutineContext) {
        val nested = Job(this.coroutineContext.job)
        try {
            block(ReaderScope(channel, this.coroutineContext + nested))
            nested.complete()

            if (this.coroutineContext.job.isCancelled) {
                channel.cancel(this.coroutineContext.job.getCancellationException())
            }
        } catch (cause: Throwable) {
            nested.cancel("Exception thrown while reading from channel", cause)
            channel.close(cause)
        } finally {
            nested.join()
        }
    }.apply {
        invokeOnCompletion {
            if (it != null && !channel.isClosedForRead) {
                channel.cancel(it)
            }
        }
    }

    return ReaderJob(channel.onClose { job.join() }, job)
}

/**
 * Reads a packet of [packet] bytes from the channel.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readPacket)
 *
 * @throws EOFException if the channel is closed before the packet is fully read.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readPacket(packet: Int): Source {
    val result = Buffer()
    while (result.size < packet) {
        if (readBuffer.exhausted()) awaitContent()
        if (isClosedForRead) break

        if (readBuffer.remaining > packet - result.size) {
            readBuffer.readTo(result, packet - result.size)
        } else {
            readBuffer.transferTo(result)
        }
    }

    if (result.size < packet) {
        throw EOFException("Not enough data available, required $packet bytes but only ${result.size} available")
    }
    return result
}

public suspend fun ByteReadChannel.discardExact(value: Long) {
    if (discard(value) < value) throw EOFException("Unable to discard $value bytes")
}

@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.discard(max: Long = Long.MAX_VALUE): Long {
    var remaining = max
    while (remaining > 0 && !isClosedForRead) {
        if (availableForRead == 0) {
            awaitContent()
        }
        val count = minOf(remaining, readBuffer.remaining)
        readBuffer.discard(count)

        remaining -= count
    }

    return max - remaining
}

private const val CR: Byte = '\r'.code.toByte()
private const val LF: Byte = '\n'.code.toByte()

/**
 * Reads a line of UTF-8 characters from the `ByteReadChannel`.
 * It recognizes CR, LF, and CRLF as line delimiters.
 *
 * ## Deprecation Notes
 *
 * This function is deprecated in favor of [readLineStrict] and [readLine].
 *
 * Changes:
 * - New functions recognize LF and CRLF as a line delimiter by default. This default comes with better performance.
 *   To keep current behavior and recognize all line delimiters (CR, LF, and CRLF), specify [LineEnding.Lenient].
 * - [readLineStrict] throws [TooLongLineException] if limit is reached before encountering a line break,
 *   similarly to current behavior. While [readLine] reads lines without limits.
 * - [readLineStrict] throws [EOFException] if the channel is closed before a line break is found,
 *   while `readUTF8Line` returns the line assuming it is full for such cases.
 *   [readLine] treats the stream end as an implicit line break, similarly to current behavior.
 * - [readLineStrict] accepts [Long] instead of [Int] as a limit parameter.
 *
 * The direct equivalent of `readUTF8Line` would be:
 * ```
 * // Before
 * val line = channel.readUTF8Line(out, max = 1024)
 *
 * // After
 * val buffer = StringBuilder()
 * val success = try {
 *     channel.readLineStrictTo(out, limit = 1024, lineEnding = LineEnding.Lenient) >= 0
 * } catch (_: EOFException) {
 *     true
 * }
 * val line = if (success) buffer.toString() else null
 * ```
 * However, we recommend to use [LineEnding.Default] if possible and verify if the case with
 * the unexpected end of line should actually be ignored. We expect the following code to be correct in most cases:
 * ```
 * val line = channel.readLineStrict(out, limit = 1024)
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readUTF8Line)
 *
 * @param max the maximum number of characters to read. Default is [Int.MAX_VALUE].
 * @return a string containing the line read, or null if channel is closed
 * @throws TooLongLineException if max is reached before encountering a line delimiter or end of input
 */
@Suppress("DEPRECATION")
@Deprecated("Use readLineStrict instead. See deprecation notes for more details.")
public suspend fun ByteReadChannel.readUTF8Line(max: Int = Int.MAX_VALUE): String? {
    val result = StringBuilder()
    val completed = readUTF8LineTo(result, max)
    return if (!completed) null else result.toString()
}

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 * It recognizes CR, LF, and CRLF as a line delimiter.
 *
 * ## Deprecation Notes
 *
 * This function is deprecated in favor of [readLineStrictTo] and [readLineTo].
 *
 * Changes:
 * - New functions recognize LF and CRLF as a line delimiter by default. This default comes with better performance.
 *   To keep current behavior and recognize all line delimiters (CR, LF, and CRLF), specify [LineEnding.Lenient].
 * - [readLineStrictTo] throws [TooLongLineException] if limit is reached before encountering a line break,
 *   similarly to current behavior. While [readLineTo] reads lines without limits.
 * - [readLineStrictTo] throws [EOFException] if the channel is closed before a line break is found,
 *   while `readUTF8LineTo` returns `true` for such cases.
 *   [readLineTo] treats the stream end as an implicit line break, similarly to current behavior.
 * - New functions return number of appended characters instead of [Boolean] or `-1` if the channel is empty.
 * - [readLineStrictTo] accepts [Long] instead of [Int] as a limit parameter.
 *
 * The direct equivalent of `readUTF8LineTo` would be:
 * ```
 * // Before
 * val success = channel.readUTF8LineTo(out, max = 1024)
 *
 * // After
 * val success = try {
 *     channel.readLineStrictTo(out, limit = 1024, lineEnding = LineEnding.Lenient) >= 0
 * } catch (_: EOFException) {
 *     true
 * }
 * ```
 * However, we recommend to use [LineEnding.Default] if possible and verify if the case with
 * the unexpected end of line should actually be ignored. We expect the following code to be correct in most cases:
 * ```
 * val success = channel.readLineTo(out, limit = 1024) >= 0
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readUTF8LineTo)
 *
 * @param out the buffer to write the line to
 * @param max the maximum number of characters to read
 *
 * @return `true` if a new line separator was found or max bytes appended. `false` if no new line separator and no bytes read.
 * @throws TooLongLineException if max is reached before encountering a newline or end of input
 */
@Suppress("DEPRECATION")
@OptIn(InternalAPI::class)
@Deprecated("Use readLineStrictTo instead. See deprecation notes for more details.")
public suspend fun ByteReadChannel.readUTF8LineTo(out: Appendable, max: Int = Int.MAX_VALUE): Boolean {
    return readUTF8LineTo(out, max, lineEnding = LineEndingMode.Any)
}

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 * It recognizes specified line endings as a line delimiter.
 * By default, all line endings (CR, LF and CRLF) are allowed as a line delimiter.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readUTF8LineTo)
 *
 * @param out the buffer to write the line to
 * @param max the maximum number of characters to read
 * @param lineEnding the allowed line endings
 *
 * @return `true` if a new line separator was found or max bytes appended. `false` if no new line separator and no bytes read.
 * @throws TooLongLineException if max is reached before encountering a newline or end of input
 */
@Suppress("DEPRECATION")
@Deprecated("Use readLineStrictTo instead.")
@InternalAPI
@OptIn(InternalIoApi::class)
public suspend fun ByteReadChannel.readUTF8LineTo(
    out: Appendable,
    max: Int = Int.MAX_VALUE,
    lineEnding: LineEndingMode = LineEndingMode.Any,
): Boolean {
    return try {
        readLineStrictTo(
            out,
            limit = max.toLong(),
            lineEnding = if (LineEndingMode.CR in lineEnding) LineEnding.Lenient else LineEnding.Default
        ) >= 0
    } catch (cause: EOFException) {
        if (cause.message?.startsWith("Unexpected end of stream after reading") == true) return true
        throw cause
    }
}

/**
 * Reads and returns a line of UTF-8 characters.
 *
 * Reads UTF-8 bytes until a line break is found or the channel is exhausted. Implicit line break
 * is assumed if the line doesn't end with a line break. Line break characters are not included in the result.
 *
 * @param lineEnding line ending mode. Accepts LF and CRLF by default.
 *
 * @return the line, or `null` if can't read from the channel
 */
public suspend fun ByteReadChannel.readLine(
    lineEnding: LineEnding = LineEnding.Default,
): String? {
    val result = StringBuilder()
    return if (readLineTo(result, lineEnding) >= 0) result.toString() else null
}

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 *
 * Appends UTF-8 bytes until a line break is found or the channel is exhausted. Implicit line break
 * is assumed if the line doesn't end with a line break. Line break characters are not included in the result.
 *
 * @param out the buffer to append line to.
 * @param lineEnding line ending mode. Accepts LF and CRLF by default.
 *
 * @return number of characters appended to [out], or `-1` if can't read from the channel
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readLineTo(
    out: Appendable,
    lineEnding: LineEnding = LineEnding.Default
): Long {
    return internalReadLineTo(
        out,
        limit = Long.MAX_VALUE,
        lenientLineEnding = lineEnding == LineEnding.Lenient,
        throwOnIncompleteLine = false,
    )
}

/**
 * Reads and returns a line of UTF-8 characters.
 * Throws an exception if the line exceeds [limit] or doesn't end with a line break.
 *
 * @param lineEnding line ending mode. Accepts LF and CRLF by default.
 *
 * @return the line, or `null` if can't read from the channel
 * @throws TooLongLineException if the line exceeds [limit]
 * @throws EOFException if the channel is closed before a line break is found
 */
public suspend fun ByteReadChannel.readLineStrict(
    limit: Long = Long.MAX_VALUE,
    lineEnding: LineEnding = LineEnding.Default,
): String? {
    val result = StringBuilder()
    return if (readLineStrictTo(result, limit, lineEnding) >= 0) result.toString() else null
}

/**
 * Reads a line of UTF-8 characters to the specified [out] buffer.
 * Throws an exception if the line exceeds [limit] or doesn't end with a line break.
 *
 * @param out the buffer to append line to
 * @param limit maximum characters to append. Unlimited by default.
 * @param lineEnding line ending mode. Accepts LF and CRLF by default.
 *
 * @return number of characters appended to [out], or `-1` if can't read from the channel
 * @throws TooLongLineException if the line exceeds [limit]
 * @throws EOFException if the channel is closed before a line break is found
 */
@OptIn(InternalAPI::class, InternalIoApi::class)
public suspend fun ByteReadChannel.readLineStrictTo(
    out: Appendable,
    limit: Long = Long.MAX_VALUE,
    lineEnding: LineEnding = LineEnding.Default,
): Long {
    require(limit >= 0) { "Limit ($limit) should be non-negative" }

    return internalReadLineTo(
        out,
        limit,
        lenientLineEnding = lineEnding == LineEnding.Lenient,
        throwOnIncompleteLine = true
    )
}

@OptIn(InternalAPI::class, InternalIoApi::class)
private suspend fun ByteReadChannel.internalReadLineTo(
    out: Appendable,
    limit: Long,
    lenientLineEnding: Boolean,
    throwOnIncompleteLine: Boolean,
): Long {
    val readBuffer = readBuffer // Get readBuffer once per line
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    var consumed = 0L

    fun transferString(count: Long) {
        if (count > 0L) {
            val string = readBuffer.readString(count)
            out.append(string)
            consumed += string.length
        }
    }

    // Should be called only if buffer[0] = CR
    suspend fun Source.discardCrlfOrCr(): Boolean {
        if ((remaining >= 2 || awaitContent(min = 2)) && buffer[1] == LF) {
            discard(2)
            return true
        }

        if (lenientLineEnding) {
            discard(1)
            return true
        }

        return false
    }

    while (consumed < limit && !isClosedForRead) {
        val limitLeft = limit - consumed
        val lfIndex = readBuffer.indexOf(LF, endIndex = limitLeft)

        if (lenientLineEnding) {
            val crEndIndex = when (lfIndex) {
                // Subtract 1 from source.remaining to ignore the last byte,
                // when RC might be part of a CRLF sequence split into two buffers
                -1L -> minOf(limitLeft, readBuffer.remaining - 1)
                0L -> 0
                // Subtract 1 from lfIndex to ignore the case when CR is part of the CRLF sequence
                else -> lfIndex - 1
            }
            val crIndex = readBuffer.indexOf(CR, endIndex = crEndIndex)

            // Sole CR in the buffer
            if (crIndex >= 0) {
                transferString(count = crIndex)
                readBuffer.discard(1)
                return consumed
            }
        }

        // Fast path. LF or CRLF in the buffer
        if (lfIndex == 0L) {
            readBuffer.discard(1)
            return consumed
        }
        if (lfIndex > 0) {
            val isCrlf = if (readBuffer.buffer[lfIndex - 1] == CR) 1L else 0L
            transferString(count = lfIndex - isCrlf)
            readBuffer.discard(1 + isCrlf)
            return consumed
        }

        val count = minOf(limitLeft, readBuffer.remaining)
        // Check for the corner case when the last byte in the buffer is CR, and LF is in the next buffer
        if (readBuffer.buffer[count - 1] == CR) {
            transferString(count = count - 1)
            if (readBuffer.discardCrlfOrCr()) return consumed
            transferString(1) // transfer the CR
        } else {
            // No new line separator
            transferString(count)
            if (consumed < limit && !awaitContent()) break
        }
    }

    if (consumed == 0L && isClosedForRead) return -1

    // Defensive check. Normally the consumed count should never exceed the limit
    if (consumed > limit) throwTooLongLineException(limit)
    if (consumed == limit) {
        // We can't read data anymore
        if (limit == Long.MAX_VALUE) throw TooLongLineException("Max line length exceeded")
        // There is no more data
        if (readBuffer.remaining == 0L && !awaitContent()) throwEndOfStreamException(consumed)

        // Corner case: line ending is right after the limit
        when (readBuffer.buffer[0]) {
            LF -> {
                readBuffer.discard(1)
                return consumed
            }

            CR -> if (readBuffer.discardCrlfOrCr()) return consumed
        }

        throwTooLongLineException(limit)
    }

    if (throwOnIncompleteLine) throwEndOfStreamException(consumed)
    return consumed
}

private fun throwTooLongLineException(limit: Long) {
    throw TooLongLineException("Line exceeds limit of $limit characters")
}

private fun throwEndOfStreamException(consumed: Long) {
    throw EOFException("Unexpected end of stream after reading $consumed characters")
}

@OptIn(InternalAPI::class, UnsafeIoApi::class, InternalIoApi::class)
public suspend inline fun ByteReadChannel.read(crossinline block: suspend (ByteArray, Int, Int) -> Int): Int {
    if (isClosedForRead) return -1
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) return -1

    var result: Int
    UnsafeBufferOperations.readFromHead(readBuffer.buffer) { array, start, endExclusive ->
        result = block(array, start, endExclusive)
        result
    }

    return result
}

@OptIn(InternalAPI::class, InternalIoApi::class)
public val ByteReadChannel.availableForRead: Int
    get() = readBuffer.buffer.size.toInt()

/**
 * Reads bytes from [start] to [end] into the provided [out] buffer, or fails
 * if the channel has been closed.
 *
 * Suspension occurs when there are not enough bytes available in the channel.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readFully)
 *
 * @param out the buffer to write to
 * @param start the index to start writing at
 * @param end the index to write until
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readFully(out: ByteArray, start: Int = 0, end: Int = out.size) {
    if (end > start && isClosedForRead) throw EOFException("Channel is already closed")
    var offset = start
    while (offset < end) {
        if (readBuffer.exhausted()) awaitContent()
        if (isClosedForRead) throw EOFException("Channel is already closed")

        val count = min(end - offset, readBuffer.remaining.toInt())
        readBuffer.readTo(out, offset, offset + count)
        offset += count
    }
}

@InternalAPI
public fun ByteReadChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}

@InternalAPI
public fun ByteWriteChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}

@InternalAPI
public fun ByteChannel.rethrowCloseCauseIfNeeded() {
    closedCause?.let { throw it }
}

/**
 * Reads bytes from the ByteReadChannel until a specified sequence of bytes is encountered or the specified limit is reached.
 *
 * This uses the KMP algorithm for finding the string match using a partial match table.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.readUntil)
 *
 * @see [Knuth–Morris–Pratt algorithm](https://en.wikipedia.org/wiki/Knuth%E2%80%93Morris%E2%80%93Pratt_algorithm)
 * @param matchString The sequence of bytes to look for.
 * @param writeChannel The channel to write the read bytes to.
 * @param limit The maximum number of bytes to read before throwing an exception.
 * @param ignoreMissing Whether to ignore the missing byteString and return the count of read bytes upon reaching the end of input.
 * @return The number of bytes read, not including the search string.
 * @throws IOException If the limit is exceeded or the byteString is not found and ignoreMissing is false.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.readUntil(
    matchString: ByteString,
    writeChannel: ByteWriteChannel,
    limit: Long = Long.MAX_VALUE,
    ignoreMissing: Boolean = false,
): Long {
    return ByteChannelScanner(
        channel = this,
        matchString = matchString,
        writeChannel = writeChannel,
        limit = limit,
    ).findNext(ignoreMissing)
}

/**
 * Skips the specified [byteString] in the ByteReadChannel if it is found at the current position.
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.skipIfFound)
 *
 * @param byteString The ByteString to look for and skip if found.
 * @return Returns `true` if the byteString was found and skipped, otherwise returns `false`.
 */
public suspend fun ByteReadChannel.skipIfFound(byteString: ByteString): Boolean {
    if (peek(byteString.size) == byteString) {
        discard(byteString.size.toLong())
        return true
    }
    return false
}

/**
 * Retrieves, but does not consume, up to the specified number of bytes from the current position in this
 * [ByteReadChannel].
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.peek)
 *
 * @param count The number of bytes to peek.
 * @return A [ByteString] containing the bytes that were peeked, or null if unable to peek the specified number of bytes.
 */
@OptIn(InternalAPI::class)
public suspend fun ByteReadChannel.peek(count: Int): ByteString? {
    if (isClosedForRead) return null
    if (!awaitContent(count)) return null
    return readBuffer.peek().readByteString(count)
}
