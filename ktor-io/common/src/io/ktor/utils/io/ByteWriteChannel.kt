package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

/**
 * Channel for asynchronous writing of sequences of bytes.
 * This is a **single-writer channel**.
 *
 * Operations on this channel cannot be invoked concurrently, unless explicitly specified otherwise
 * in description. Exceptions are [close] and [flush].
 */
public expect interface ByteWriteChannel {
    /**
     * Returns number of bytes that can be written without suspension. Write operations do no suspend and return
     * immediately when this number is at least the number of bytes requested for write.
     */
    public val availableForWrite: Int

    /**
     * Returns `true` is channel has been closed and attempting to write to the channel will cause an exception.
     */
    public val isClosedForWrite: Boolean

    /**
     * Returns `true` if channel flushes automatically all pending bytes after every write function call.
     * If `false` then flush only happens at manual [flush] invocation or when the buffer is full.
     */
    public val autoFlush: Boolean

    /**
     * Number of bytes written to the channel.
     * It is not guaranteed to be atomic so could be updated in the middle of write operation.
     */
    public val totalBytesWritten: Long

    /**
     * An closure cause exception or `null` if closed successfully or not yet closed
     */
    public val closedCause: Throwable?

    /**
     * Writes as much as possible and only suspends if buffer is full
     */
    public suspend fun writeAvailable(src: ByteArray, offset: Int, length: Int): Int

    public suspend fun writeAvailable(src: ChunkBuffer): Int

    /**
     * Writes all [src] bytes and suspends until all bytes written. Causes flush if buffer filled up or when [autoFlush]
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeFully(src: ByteArray, offset: Int, length: Int)

    public suspend fun writeFully(src: Buffer)

    public suspend fun writeFully(memory: Memory, startIndex: Int, endIndex: Int)

    @Suppress("DEPRECATION")
    @Deprecated("Use write { } instead.")
    public suspend fun writeSuspendSession(visitor: suspend WriterSuspendSession.() -> Unit)

    /**
     * Writes a [packet] fully or fails if channel get closed before the whole packet has been written
     */
    public suspend fun writePacket(packet: ByteReadPacket)

    /**
     * Writes long number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeLong(l: Long)

    /**
     * Writes int number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeInt(i: Int)

    /**
     * Writes short number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeShort(s: Short)

    /**
     * Writes byte and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeByte(b: Byte)

    /**
     * Writes double number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeDouble(d: Double)

    /**
     * Writes float number and suspends until written.
     * Crashes if channel get closed while writing.
     */
    public suspend fun writeFloat(f: Float)

    /**
     * Invokes [block] when at least 1 byte is available for write.
     */
    @ExperimentalIoApi
    public suspend fun awaitFreeSpace()

    /**
     * Closes this channel with an optional exceptional [cause].
     * It flushes all pending write bytes (via [flush]).
     * This is an idempotent operation -- repeated invocations of this function have no effect and return `false`.
     *
     * A channel that was closed without a [cause], is considered to be _closed normally_.
     * A channel that was closed with non-null [cause] is called a _failed channel_. Attempts to read or
     * write on a failed channel throw this cause exception.
     *
     * After invocation of this operation [isClosedForWrite] starts returning `true` and
     * all subsequent write operations throw [ClosedWriteChannelException] or the specified [cause].
     * However, [isClosedForRead][ByteReadChannel.isClosedForRead] on the side of [ByteReadChannel]
     * starts returning `true` only after all written bytes have been read.
     *
     * Please note that if the channel has been closed with cause and it has been provided by [reader] or [writer]
     * coroutine then the corresponding coroutine will be cancelled with [cause]. If no [cause] provided then no
     * cancellation will be propagated.
     */
    public fun close(cause: Throwable?): Boolean

    /**
     * Flushes all pending write bytes making them available for read.
     *
     * This function is thread-safe and can be invoked in any thread at any time.
     * It does nothing when invoked on a closed channel.
     */
    public fun flush()
}

public suspend fun ByteWriteChannel.writeAvailable(src: ByteArray): Int = writeAvailable(src, 0, src.size)
public suspend fun ByteWriteChannel.writeFully(src: ByteArray): Unit = writeFully(src, 0, src.size)

public suspend fun ByteWriteChannel.writeShort(s: Int) {
    return writeShort((s and 0xffff).toShort())
}

public suspend fun ByteWriteChannel.writeShort(s: Int, byteOrder: ByteOrder) {
    return writeShort((s and 0xffff).toShort(), byteOrder)
}

public suspend fun ByteWriteChannel.writeByte(b: Int) {
    return writeByte((b and 0xff).toByte())
}

public suspend fun ByteWriteChannel.writeInt(i: Long) {
    return writeInt(i.toInt())
}

public suspend fun ByteWriteChannel.writeInt(i: Long, byteOrder: ByteOrder) {
    return writeInt(i.toInt(), byteOrder)
}

/**
 * Closes this channel with no failure (successfully)
 */
public fun ByteWriteChannel.close(): Boolean = close(null)

public suspend fun ByteWriteChannel.writeStringUtf8(s: CharSequence) {
    val packet = buildPacket {
        writeStringUtf8(s)
    }

    return writePacket(packet)
}

/*
TODO
public suspend fun ByteWriteChannel.writeStringUtf8(s: CharBuffer) {
    val packet = buildPacket {
        writeStringUtf8(s)
    }

    return writePacket(packet)
}*/

public suspend fun ByteWriteChannel.writeStringUtf8(s: String) {
    val packet = buildPacket {
        writeText(s)
    }

    return writePacket(packet)
}

public suspend fun ByteWriteChannel.writeBoolean(b: Boolean) {
    return writeByte(if (b) 1 else 0)
}

/**
 * Writes UTF16 character
 */
public suspend fun ByteWriteChannel.writeChar(ch: Char) {
    return writeShort(ch.toInt())
}

public suspend inline fun ByteWriteChannel.writePacket(headerSizeHint: Int = 0, builder: BytePacketBuilder.() -> Unit) {
    return writePacket(buildPacket(headerSizeHint, builder))
}

public suspend fun ByteWriteChannel.writePacketSuspend(builder: suspend BytePacketBuilder.() -> Unit) {
    return writePacket(buildPacket { builder() })
}

/**
 * Indicates attempt to write on [isClosedForWrite][ByteWriteChannel.isClosedForWrite] channel
 * that was closed without a cause. A _failed_ channel rethrows the original [close][ByteWriteChannel.close] cause
 * exception on send attempts.
 */
public class ClosedWriteChannelException(message: String?) : CancellationException(message)
