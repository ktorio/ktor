package io.ktor.utils.io

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*

/**
 * Await until at least [desiredSize] is available for read or EOF and invoke [block] function. The block function
 * should never capture a provided [Memory] instance outside otherwise an undefined behaviour may occur including
 * accidental crash or data corruption. Block function should return number of bytes consumed or 0.
 *
 * Specifying [desiredSize] larger than the channel's capacity leads to block function invocation earlier
 * when the channel is full. So specifying too big [desiredSize] is identical to specifying [desiredSize] equal to
 * the channel's capacity. The other case when a provided memory range could be less than [desiredSize] is that
 * all the requested bytes couldn't be represented as a single memory range due to internal implementation reasons.
 *
 * @return number of bytes consumed, possibly 0
 */
@ExperimentalIoApi
suspend inline fun ByteReadChannel.read(
    desiredSize: Int = 1,
    block: (source: Memory, start: Long, endExclusive: Long) -> Int
): Int {
    val buffer = requestBuffer(desiredSize) ?: Buffer.Empty
    var bytesRead = 0
    try {
        bytesRead = block(buffer.memory, buffer.readPosition.toLong(), buffer.readRemaining.toLong())
        return bytesRead
    } finally {
        completeReadingFromBuffer(buffer, bytesRead)
    }
}

@Deprecated("Use read { } instead.")
interface ReadSession {
    /**
     * Number of bytes available for read. However it doesn't necessarily means that all available bytes could be
     * requested at once
     */
    val availableForRead: Int

    /**
     * Discard at most [n] available bytes or 0 if no bytes available yet
     * @return number of bytes actually discarded, could be 0
     */
    fun discard(n: Int): Int

    /**
     * Request buffer range [atLeast] bytes length
     *
     * There are the following reasons for this function to return `null`:
     * - not enough bytes available yet (should be at least `atLeast` bytes available)
     * - due to buffer fragmentation it is impossible to represent the requested range as a single buffer range
     * - end of stream encountered and all bytes were consumed
     *
     * @return buffer for the requested range or `null` if it is impossible to provide such a buffer view
     * @throws Throwable if the channel has been closed with an exception or cancelled
     */
    @Suppress("DEPRECATION")
    fun request(atLeast: Int = 1): IoBuffer?
}

@Suppress("DEPRECATION")
@Deprecated("Use read { } instead.")
interface SuspendableReadSession : ReadSession {
    /**
     * Suspend until [atLeast] bytes become available or end of stream encountered (possibly due to exceptional close)
     *
     * @return true if there are [atLeast] bytes available or false if end of stream encountered (there still could be
     * bytes available but less than [atLeast])
     * @throws Throwable if the channel has been closed with an exception or cancelled
     * @throws IllegalArgumentException if [atLeast] is negative to too big (usually bigger that 4088)
     */
    suspend fun await(atLeast: Int = 1): Boolean
}

@PublishedApi
internal suspend fun ByteReadChannel.requestBuffer(desiredSize: Int): Buffer? {
    @Suppress("DEPRECATION")
    val readSession: SuspendableReadSession? = when {
        this is SuspendableReadSession -> this
        this is HasReadSession -> startReadSession()
        else -> null
    }

    if (readSession != null) {
        val buffer = readSession.request(desiredSize.coerceAtMost(Buffer.ReservedSize))
        if (buffer != null) {
            return buffer
        }

        return readSession.requestBufferSuspend(desiredSize)
    }

    return requestBufferFallback(desiredSize)
}

@PublishedApi
internal suspend fun ByteReadChannel.completeReadingFromBuffer(buffer: Buffer?, bytesRead: Int) {
    @Suppress("DEPRECATION")
    val readSession: SuspendableReadSession? = readSessionFor()

    if (readSession != null) {
        readSession.discard(bytesRead)
        return
    }

    if (buffer is ChunkBuffer) {
        buffer.release(ChunkBuffer.Pool)
        discard(bytesRead.toLong())
    }
}

@Suppress("DEPRECATION")
private suspend fun SuspendableReadSession.requestBufferSuspend(desiredSize: Int): Buffer? {
    await(desiredSize)
    return request(1)
}

private suspend fun ByteReadChannel.requestBufferFallback(desiredSize: Int): ChunkBuffer {
    val chunk = ChunkBuffer.Pool.borrow()
    val copied =
        peekTo(chunk.memory, chunk.writePosition.toLong(), 0L, desiredSize.toLong(), chunk.writeRemaining.toLong())
    chunk.commitWritten(copied.toInt())

    return chunk
}

internal interface HasReadSession {
    @Suppress("DEPRECATION")
    fun startReadSession(): SuspendableReadSession

    fun endReadSession()
}

@Suppress("DEPRECATION", "NOTHING_TO_INLINE")
private inline fun ByteReadChannel.readSessionFor(): SuspendableReadSession? = when {
    this is HasReadSession -> startReadSession()
    else -> null
}
