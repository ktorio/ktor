package io.ktor.utils.io

import io.ktor.utils.io.internal.*
import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.ByteOrder
import java.nio.*

/**
 * Channel for asynchronous reading of sequences of bytes.
 * This is a **single-reader channel**.
 *
 * Operations on this channel cannot be invoked concurrently.
 */
actual interface ByteReadChannel {
    /**
     * Returns number of bytes that can be read without suspension. Read operations do no suspend and return
     * immediately when this number is at least the number of bytes requested for read.
     */
    actual val availableForRead: Int

    /**
     * Returns `true` if the channel is closed and no remaining bytes are available for read.
     * It implies that [availableForRead] is zero.
     */
    actual val isClosedForRead: Boolean

    actual val isClosedForWrite: Boolean

    /**
     * Byte order that is used for multi-byte read operations
     * (such as [readShort], [readInt], [readLong], [readFloat], and [readDouble]).
     */
    @Deprecated(
        "Setting byte order is no longer supported. Read/write in big endian and use reverseByteOrder() extensions.",
        level = DeprecationLevel.ERROR
    )
    actual var readByteOrder: ByteOrder

    /**
     * Number of bytes read from the channel.
     * It is not guaranteed to be atomic so could be updated in the middle of long running read operation.
     */
    @Deprecated("Don't use byte count")
    actual val totalBytesRead: Long

    /**
     * Reads all available bytes to [dst] buffer and returns immediately or suspends if no bytes available
     * @return number of bytes were read or `-1` if the channel has been closed
     */
    actual suspend fun readAvailable(dst: ByteArray, offset: Int, length: Int): Int
    actual suspend fun readAvailable(dst: IoBuffer): Int
    suspend fun readAvailable(dst: ByteBuffer): Int

    /**
     * Reads all [length] bytes to [dst] buffer or fails if channel has been closed.
     * Suspends if not enough bytes available.
     */
    actual suspend fun readFully(dst: ByteArray, offset: Int, length: Int)
    actual suspend fun readFully(dst: IoBuffer, n: Int)
    suspend fun readFully(dst: ByteBuffer): Int

    /**
     * Reads the specified amount of bytes and makes a byte packet from them. Fails if channel has been closed
     * and not enough bytes available. Accepts [headerSizeHint] to be provided, see [WritePacket].
     */
    actual suspend fun readPacket(size: Int, headerSizeHint: Int): ByteReadPacket

    /**
     * Reads up to [limit] bytes and makes a byte packet or until end of stream encountered.
     * Accepts [headerSizeHint] to be provided, see [BytePacketBuilder].
     */
    actual suspend fun readRemaining(limit: Long, headerSizeHint: Int): ByteReadPacket

    /**
     * Reads a long number (suspending if not enough bytes available) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readLong(): Long

    /**
     * Reads an int number (suspending if not enough bytes available) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readInt(): Int

    /**
     * Reads a short number (suspending if not enough bytes available) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readShort(): Short

    /**
     * Reads a byte (suspending if no bytes available yet) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readByte(): Byte

    /**
     * Reads a boolean value (suspending if no bytes available yet) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readBoolean(): Boolean

    /**
     * Reads double number (suspending if not enough bytes available) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readDouble(): Double

    /**
     * Reads float number (suspending if not enough bytes available) or fails if channel has been closed
     * and not enough bytes.
     */
    actual suspend fun readFloat(): Float

    /**
     * For every available bytes range invokes [visitor] function until it return false or end of stream encountered
     */
    @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
    suspend fun consumeEachBufferRange(visitor: ConsumeEachBufferVisitor) {
        consumeEachBufferRange(visitor)
    }

    /**
     * Starts non-suspendable read session. After channel preparation [consumer] lambda will be invoked immediately
     * event if there are no bytes available for read yet.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use read { } instead.")
    actual fun readSession(consumer: ReadSession.() -> Unit)

    /**
     * Starts a suspendable read session. After channel preparation [consumer] lambda will be invoked immediately
     * even if there are no bytes available for read yet. [consumer] lambda could suspend as much as needed.
     */
    @Suppress("DEPRECATION")
    @Deprecated("Use read { } instead.")
    actual suspend fun readSuspendableSession(consumer: suspend SuspendableReadSession.() -> Unit)

    @Suppress("DEPRECATION")
    @Deprecated("Use read { } instead.")
    fun <R> lookAhead(visitor: LookAheadSession.() -> R): R

    @Suppress("DEPRECATION")
    @Deprecated("Use read { } instead.")
    suspend fun <R> lookAheadSuspend(visitor: suspend LookAheadSuspendSession.() -> R): R

    /**
     * Reads a line of UTF-8 characters to the specified [out] buffer up to [limit] characters.
     * Supports both CR-LF and LF line endings.
     * Throws an exception if the specified [limit] has been exceeded.
     *
     * @return `true` if line has been read (possibly empty) or `false` if channel has been closed
     * and no characters were read.
     */
    actual suspend fun <A : Appendable> readUTF8LineTo(out: A, limit: Int): Boolean

    /**
     * Reads a line of UTF-8 characters up to [limit] characters.
     * Supports both CR-LF and LF line endings.
     * Throws an exception if the specified [limit] has been exceeded.
     *
     * @return a line string with no line endings or `null` of channel has been closed
     * and no characters were read.
     */
    actual suspend fun readUTF8Line(limit: Int): String?

    /**
     * Invokes [consumer] when it will be possible to read at least [min] bytes
     * providing byte buffer to it so lambda can read from the buffer
     * up to [ByteBuffer.remaining] bytes. If there are no [min] bytes available then the invocation could
     * suspend until the requirement will be met.
     *
     * If [min] is zero then the invocation will suspend until at least one byte available.
     *
     * Warning: it is not guaranteed that all of remaining bytes will be represented as a single byte buffer
     * eg: it could be 4 bytes available for read but the provided byte buffer could have only 2 remaining bytes:
     * in this case you have to invoke read again (with decreased [min] accordingly).
     *
     * It will fail with [EOFException] if not enough bytes ([availableForRead] < [min]) available
     * in the channel after it is closed.
     *
     * [consumer] lambda should modify buffer's position accordingly. It also could temporarily modify limit however
     * it should restore it before return. It is not recommended to access any bytes of the buffer outside of the
     * provided byte range [position(); limit()) as there could be any garbage or incomplete data.
     *
     * @param min amount of bytes available for read, should be positive or zero
     * @param consumer to be invoked when at least [min] bytes available for read
     */
    suspend fun read(min: Int = 1, consumer: (ByteBuffer) -> Unit)

    /**
     * Close channel with optional [cause] cancellation. Unlike [ByteWriteChannel.close] that could close channel
     * normally, cancel does always close with error so any operations on this channel will always fail
     * and all suspensions will be resumed with exception.
     *
     * Please note that if the channel has been provided by [reader] or [writer] then the corresponding owning
     * coroutine will be cancelled as well
     *
     * @see ByteWriteChannel.close
     */
    actual fun cancel(cause: Throwable?): Boolean

    /**
     * Discard up to [max] bytes
     *
     * @return number of bytes were discarded
     */
    actual suspend fun discard(max: Long): Long

    /**
     * Try to copy at least [min] but up to [max] bytes to the specified [destination] buffer from this input
     * skipping [offset] bytes. If there are not enough bytes available to provide [min] bytes after skipping [offset]
     * bytes then it will trigger the underlying source reading first and after that will
     * simply copy available bytes even if EOF encountered so [min] is not a requirement but a desired number of bytes.
     * It is safe to specify [max] greater than the destination free space.
     * `min` shouldn't be bigger than the [destination] free space.
     * This function could trigger the underlying source suspending reading.
     * It is allowed to specify too big [offset] so in this case this function will always return `0` after prefetching
     * all underlying bytes but note that it may lead to significant memory consumption.
     * This function usually copy more bytes than [min] (unless `max = min`) but it is not guaranteed.
     * When `0` is returned with `offset = 0` then it makes sense to check [endOfInput].
     *
     * @param destination to write bytes
     * @param offset to skip input
     * @param min bytes to be copied, shouldn't be greater than the buffer free space. Could be `0`.
     * @param max bytes to be copied even if there are more bytes buffered, could be [Int.MAX_VALUE].
     * @return number of bytes copied to the [destination] possibly `0`
     */
    actual suspend fun peekTo(
        destination: Memory,
        destinationOffset: Long,
        offset: Long,
        min: Long,
        max: Long
    ): Long

    actual companion object {
        actual val Empty: ByteReadChannel by lazy { ByteChannel().apply { close() } }
    }
}

actual suspend fun ByteReadChannel.joinTo(dst: ByteWriteChannel, closeOnEnd: Boolean) {
    require(dst !== this)

    if (this is ByteBufferChannel && dst is ByteBufferChannel) {
        return dst.joinFrom(this, closeOnEnd)
    }

    return joinToImplSuspend(dst, closeOnEnd)
}

private suspend fun ByteReadChannel.joinToImplSuspend(dst: ByteWriteChannel, close: Boolean) {
    copyTo(dst, Long.MAX_VALUE)
    if (close) {
        dst.close()
    } else {
        dst.flush()
    }
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of copied bytes
 */
actual suspend fun ByteReadChannel.copyTo(dst: ByteWriteChannel, limit: Long): Long {
    require(this !== dst)
    require(limit >= 0L)

    if (this is ByteBufferChannel && dst is ByteBufferChannel) {
        return dst.copyDirect(this, limit, null)
    } else if (this is ByteChannelSequentialBase && dst is ByteChannelSequentialBase) {
        return copyToSequentialImpl(dst, Long.MAX_VALUE) // more specialized extension function
    }

    return copyToImpl(dst, limit)
}

private suspend fun ByteReadChannel.copyToImpl(dst: ByteWriteChannel, limit: Long): Long {
    val buffer = IoBuffer.Pool.borrow()
    val dstNeedsFlush = !dst.autoFlush

    try {
        var copied = 0L

        while (true) {
            val remaining = limit - copied
            if (remaining == 0L) break
            buffer.resetForWrite(minOf(buffer.capacity.toLong(), remaining).toInt())

            val size = readAvailable(buffer)
            if (size == -1) break

            dst.writeFully(buffer)
            copied += size

            if (dstNeedsFlush && availableForRead == 0) {
                dst.flush()
            }
        }
        return copied
    } catch (t: Throwable) {
        dst.close(t)
        throw t
    } finally {
        buffer.release(IoBuffer.Pool)
    }
}

/**
 * TODO
 * Reads all the bytes from receiver channel and builds a packet that is returned unless the specified [limit] exceeded.
 * It will simply stop reading and return packet of size [limit] in this case
 */
/*suspend fun ByteReadChannel.readRemaining(limit: Int = Int.MAX_VALUE): ByteReadPacket {
    val buffer = JavaNioAccess.BufferPool.borrow()
    val packet = WritePacket()

    try {
        var copied = 0L

        while (copied < limit) {
            buffer.clear()
            if (limit - copied < buffer.limit()) {
                buffer.limit((limit - copied).toInt())
            }
            val size = readAvailable(buffer)
            if (size == -1) break

            buffer.flip()
            packet.writeFully(buffer)
            copied += size
        }

        return packet.build()
    } catch (t: Throwable) {
        packet.release()
        throw t
    } finally {
        JavaNioAccess.BufferPool.recycle(buffer)
    }
}*/
