package io.ktor.utils.io.core

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlin.contracts.*

/**
 * A read-write facade to actual buffer of fixed size. Multiple views could share the same actual buffer.
 * Concurrent unsafe. The only concurrent-safe operation is [release].
 * In most cases [ByteReadPacket] and [BytePacketBuilder] should be used instead.
 */
@Suppress("DIFFERENT_NAMES_FOR_THE_SAME_PARAMETER_IN_SUPERTYPES", "DEPRECATION")
@Deprecated("Use Memory, Input or Output instead.")
expect class IoBuffer : Input, Output, ChunkBuffer {

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(memory: Memory, origin: ChunkBuffer?)

    @Deprecated(
        "Not supported anymore. All operations are big endian by default. " +
            "Read/write with readXXXLittleEndian/writeXXXLittleEndian or " +
            "do readXXX/writeXXX with X.reverseByteOrder() instead.",
        level = DeprecationLevel.ERROR
    )
    final override var byteOrder: ByteOrder

    override fun close()

    final override fun flush()

    fun release(pool: ObjectPool<IoBuffer>)

    @Suppress("DEPRECATION")
    companion object {
        /**
         * Number of bytes usually reserved in the end of chunk
         * when several instances of [ChunkBuffer] are connected into a chain (usually inside of [ByteReadPacket]
         * or [BytePacketBuilder])
         */
        @DangerousInternalIoApi
        val ReservedSize: Int

        /**
         * The empty buffer singleton: it has zero capacity for read and write.
         */
        val Empty: IoBuffer

        /**
         * The default buffer pool
         */
        val Pool: ObjectPool<IoBuffer>

        /**
         * Pool that always instantiates new buffers instead of reusing it
         */
        val NoPool: ObjectPool<IoBuffer>

        /**
         * A pool that always returns [IoBuffer.Empty]
         */
        val EmptyPool: ObjectPool<IoBuffer>
    }
}

/**
 * Read the specified number of bytes specified (optional, read all remaining by default)
 */
fun Buffer.readBytes(count: Int = readRemaining): ByteArray {
    if (count == 0) {
        return EmptyByteArray
    }

    val result = ByteArray(count)
    readFully(result)
    return result
}

@Suppress("DEPRECATION")
internal fun IoBuffer.releaseImpl(pool: ObjectPool<IoBuffer>) {
    if (release()) {
        val origin = origin
        if (origin is IoBuffer) {
            unlink()
            origin.release(pool)
        } else {
            pool.recycle(this)
        }
    }
}

@Suppress("DEPRECATION", "DEPRECATION_ERROR")
internal object EmptyBufferPoolImpl : NoPoolImpl<IoBuffer>() {
    override fun borrow() = IoBuffer.Empty
}

internal tailrec fun ChunkBuffer?.releaseAll(pool: ObjectPool<ChunkBuffer>) {
    if (this == null) return
    val next = cleanNext()
    release(pool)
    next.releaseAll(pool)
}

internal inline fun ChunkBuffer.forEachChunk(block: (ChunkBuffer) -> Unit) {
    contract {
        callsInPlace(block, InvocationKind.AT_LEAST_ONCE)
    }
    var current = this
    do {
        block(current)
        current = current.next ?: break
    } while (true)
}

/**
 * Copy every element of the chain starting from this and setup next links.
 */
internal fun ChunkBuffer.copyAll(): ChunkBuffer {
    val copied = duplicate()
    val next = this.next ?: return copied

    return next.copyAll(copied, copied)
}

private tailrec fun ChunkBuffer.copyAll(head: ChunkBuffer, prev: ChunkBuffer): ChunkBuffer {
    val copied = duplicate()
    prev.next = copied

    val next = this.next ?: return head

    return next.copyAll(head, copied)
}

internal tailrec fun ChunkBuffer.findTail(): ChunkBuffer {
    val next = this.next ?: return this
    return next.findTail()
}

/**
 * Summarize remainings of all elements of the chain
 */
@DangerousInternalIoApi
fun ChunkBuffer.remainingAll(): Long = remainingAll(0L)

@Suppress("DEPRECATION", "UNUSED")
@Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
fun remainingAll(buffer: IoBuffer): Long = buffer.remainingAll()

private tailrec fun ChunkBuffer.remainingAll(n: Long): Long {
    val rem = readRemaining.toLong() + n
    val next = this.next ?: return rem

    return next.remainingAll(rem)
}

internal tailrec fun ChunkBuffer.isEmpty(): Boolean {
    if (readRemaining > 0) return false
    val next = this.next ?: return true
    return next.isEmpty()
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Long.coerceAtMostMaxInt(): Int = minOf(this, Int.MAX_VALUE.toLong()).toInt()

@Suppress("NOTHING_TO_INLINE")
internal inline fun Long.coerceAtMostMaxIntOrFail(message: String): Int {
    if (this > Int.MAX_VALUE.toLong()) throw IllegalArgumentException(message)
    return this.toInt()
}

internal fun Buffer.peekTo(destination: Memory, destinationOffset: Long, offset: Long, min: Long, max: Long): Long {
    val size = minOf(
        destination.size - destinationOffset,
        max,
        readRemaining.toLong()
    )

    memory.copyTo(
        destination,
        readPosition + offset,
        size,
        destinationOffset
    )

    return size
}

class BufferLimitExceededException(message: String) : Exception(message)
