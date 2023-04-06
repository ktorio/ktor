/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import kotlinx.coroutines.*

/**
 * Creates buffered channel for asynchronous reading and writing of sequences of bytes.
 */
@Suppress("DEPRECATION")
public actual fun ByteChannel(autoFlush: Boolean): ByteChannel {
    return ByteChannelJS(ChunkBuffer.Empty, autoFlush)
}

/**
 * Creates channel for reading from the specified byte array.
 */
public actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel {
    if (content.isEmpty()) return ByteReadChannel.Empty
    @Suppress("DEPRECATION")
    val head = ChunkBuffer.Pool.borrow()
    var tail = head

    var start = offset
    val end = start + length
    while (true) {
        tail.reserveEndGap(8)
        val size = minOf(end - start, tail.writeRemaining)
        @Suppress("DEPRECATION")
        (tail as Buffer).writeFully(content, start, size)
        start += size

        if (start == end) break
        val current = tail
        @Suppress("DEPRECATION")
        tail = ChunkBuffer.Pool.borrow()
        current.next = tail
    }

    return ByteChannelJS(head, false).apply { close() }
}

public actual suspend fun ByteReadChannel.joinTo(dst: ByteWriteChannel, closeOnEnd: Boolean) {
    (this as ByteChannelSequentialBase).joinToImpl((dst as ByteChannelSequentialBase), closeOnEnd)
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of copied bytes
 */
public actual suspend fun ByteReadChannel.copyTo(dst: ByteWriteChannel, limit: Long): Long {
    return (this as ByteChannelSequentialBase).copyToSequentialImpl((dst as ByteChannelSequentialBase), limit)
}

@Suppress("DEPRECATION")
internal class ByteChannelJS(initial: ChunkBuffer, autoFlush: Boolean) : ByteChannelSequentialBase(initial, autoFlush) {
    private var attachedJob: Job? = null

    @OptIn(InternalCoroutinesApi::class)
    @Deprecated(IO_DEPRECATION_MESSAGE)
    override fun attachJob(job: Job) {
        attachedJob?.cancel()
        attachedJob = job
        job.invokeOnCompletion(onCancelling = true) { cause ->
            attachedJob = null
            if (cause != null) {
                cancel(cause.unwrapCancellationException())
            }
        }
    }

    override fun toString(): String = "ByteChannel[$attachedJob, ${hashCode()}]"
}
