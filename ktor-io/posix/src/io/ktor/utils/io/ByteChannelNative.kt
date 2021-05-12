/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import io.ktor.utils.io.concurrent.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*

/**
 * Creates buffered channel for asynchronous reading and writing of sequences of bytes.
 */
public actual fun ByteChannel(autoFlush: Boolean): ByteChannel {
    return ByteChannelNative(IoBuffer.Empty, autoFlush)
}

/**
 * Creates channel for reading from the specified byte array.
 */
public actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel {
    if (content.isEmpty()) return ByteReadChannel.Empty
    val head = IoBuffer.Pool.borrow()
    var tail = head

    var start = offset
    val end = start + length
    while (true) {
        tail.reserveEndGap(8)
        val size = minOf(end - start, tail.writeRemaining)
        (tail as Buffer).writeFully(content, start, size)
        start += size

        if (start == end) break

        val current = tail
        tail = IoBuffer.Pool.borrow()
        current.next = tail
    }

    return ByteChannelNative(head, false).apply { close() }
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

internal class ByteChannelNative(
    initial: IoBuffer,
    autoFlush: Boolean,
    pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool
) : ByteChannelSequentialBase(initial, autoFlush, pool) {
    private var attachedJob: Job? by shared(null)

    init {
        makeShared()
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun attachJob(job: Job) {
        attachedJob?.cancel()
        attachedJob = job
        job.invokeOnCompletion(onCancelling = true) { cause ->
            attachedJob = null
            if (cause != null) cancel(cause)
        }
    }

    override suspend fun readAvailable(dst: CPointer<ByteVar>, offset: Int, length: Int): Int {
        return readAvailable(dst, offset.toLong(), length.toLong())
    }

    override suspend fun readAvailable(dst: CPointer<ByteVar>, offset: Long, length: Long): Int {
        require(offset >= 0L)
        require(length >= 0L)
        closedCause?.let { throw it }
        if (closed && availableForRead == 0) return -1

        if (length == 0L) return 0

        if (availableForRead == 0) {
            awaitSuspend(1)
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val size = tryReadCPointer(dst, offset, length)
        afterRead(size)
        return size
    }

    override suspend fun readFully(dst: CPointer<ByteVar>, offset: Int, length: Int) {
        return readFully(dst, offset.toLong(), length.toLong())
    }

    override suspend fun readFully(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        require(offset >= 0L)
        require(length >= 0L)

        return when {
            closedCause != null -> throw closedCause!!
            readable.remaining >= length -> {
                val size = tryReadCPointer(dst, offset, length)
                afterRead(size)
            }
            closed -> throw EOFException(
                "Channel is closed and not enough bytes available: required $length but $availableForRead available"
            )
            else -> readFullySuspend(dst, offset, length)
        }
    }

    private suspend fun readFullySuspend(dst: CPointer<ByteVar>, offset: Long, length: Long) {
        var position = offset
        var rem = length

        while (rem > 0) {
            val rc = readAvailable(dst, position, rem).toLong()
            if (rc == -1L) break
            position += rc
            rem -= rc
        }

        if (rem > 0) {
            throw EOFException(
                "Channel is closed and not enough bytes available: required $rem but $availableForRead available"
            )
        }
    }

    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Int, length: Int) {
        return writeFully(src, offset.toLong(), length.toLong())
    }

    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
        if (availableForWrite > 0) {
            val size = tryWriteCPointer(src, offset, length).toLong()

            if (length == size) {
                afterWrite(size.toInt())
                return
            }

            flush()

            return writeFullySuspend(src, offset + size, length - size)
        }

        return writeFullySuspend(src, offset, length)
    }

    private suspend fun writeFullySuspend(src: CPointer<ByteVar>, offset: Long, length: Long) {
        var rem = length
        var position = offset

        while (rem > 0) {
            awaitAtLeastNBytesAvailableForWrite(1)
            val size = tryWriteCPointer(src, position, rem).toLong()
            rem -= size
            position += size
            if (rem > 0) flush()
            else afterWrite(size.toInt())
        }
    }

    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Int, length: Int): Int {
        return writeAvailable(src, offset.toLong(), length.toLong())
    }

    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        if (availableForWrite > 0) {
            val size = tryWriteCPointer(src, offset, length)
            afterWrite(size)
            return size
        }

        return writeAvailableSuspend(src, offset, length)
    }

    override fun close(cause: Throwable?): Boolean {
        val close = super.close(cause)
        val job = attachedJob
        if (close && job != null && cause != null) {
            if (cause is CancellationException) {
                job.cancel(cause)
            } else {
                job.cancel("Channel is cancelled", cause)
            }
        }

        return close
    }

    override fun toString(): String {
        val hashCode = hashCode().toString(16)
        return "ByteChannel[0x$hashCode, job: $attachedJob, cause: $closedCause]"
    }

    private suspend fun writeAvailableSuspend(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src, offset, length)
    }

    private fun tryWriteCPointer(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        val size = minOf(length, availableForWrite.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val ptr: CPointer<ByteVar> = (src + offset)!!
        writable.writeFully(ptr, 0, size)
        return size
    }

    private fun tryReadCPointer(dst: CPointer<ByteVar>, offset: Long, length: Long): Int {
        val size = minOf(length, availableForRead.toLong(), Int.MAX_VALUE.toLong()).toInt()
        val ptr: CPointer<ByteVar> = (dst + offset)!!
        readable.readFully(ptr, 0, size)
        return size
    }
}
