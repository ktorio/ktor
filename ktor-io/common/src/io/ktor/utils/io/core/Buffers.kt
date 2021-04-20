package io.ktor.utils.io.core

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.pool.*
import kotlin.contracts.*

/**
 * Read the specified number of bytes specified (optional, read all remaining by default)
 */
public fun Buffer.readBytes(count: Int = readRemaining): ByteArray {
    if (count == 0) {
        return EmptyByteArray
    }

    val result = ByteArray(count)
    readFully(result)
    return result
}

internal fun ChunkBuffer?.releaseAll(pool: ObjectPool<ChunkBuffer>) {
    var current = this
    while (current != null) {
        val next = current.cleanNext()
        current.release(pool)
        current = next
    }
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
public fun ChunkBuffer.remainingAll(): Long = remainingAll(0L)

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

public class BufferLimitExceededException(message: String) : Exception(message)
