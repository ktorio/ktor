package io.ktor.utils.io

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.core.internal.ChunkBuffer
import io.ktor.utils.io.pool.ObjectPool


/**
 * Creates buffered channel for asynchronous reading and writing of sequences of bytes.
 */
actual fun ByteChannel(autoFlush: Boolean): ByteChannel {
    return ByteChannelNative(IoBuffer.Empty, autoFlush)
}

/**
 * Creates channel for reading from the specified byte array.
 */
actual fun ByteReadChannel(content: ByteArray, offset: Int, length: Int): ByteReadChannel {
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
        tail = IoBuffer.Pool.borrow()
    }

    return ByteChannelNative(head, false).apply { close() }
}

actual suspend fun ByteReadChannel.joinTo(dst: ByteWriteChannel, closeOnEnd: Boolean) {
    (this as ByteChannelSequentialBase).joinToImpl((dst as ByteChannelSequentialBase), closeOnEnd)
}

/**
 * Reads up to [limit] bytes from receiver channel and writes them to [dst] channel.
 * Closes [dst] channel if fails to read or write with cause exception.
 * @return a number of copied bytes
 */
actual suspend fun ByteReadChannel.copyTo(dst: ByteWriteChannel, limit: Long): Long {
    return (this as ByteChannelSequentialBase).copyToSequentialImpl((dst as ByteChannelSequentialBase), Long.MAX_VALUE)
}

internal class ByteChannelNative(initial: IoBuffer,
                                 autoFlush: Boolean,
                                 pool: ObjectPool<ChunkBuffer> = ChunkBuffer.Pool) :
    ByteChannelSequentialBase(initial, autoFlush, pool) {
    private var attachedJob: Job? = null

    @UseExperimental(InternalCoroutinesApi::class)
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

        return when {
            closedCause != null -> throw closedCause!!
            readable.canRead() -> {
                val size = tryReadCPointer(dst, offset, length)
                afterRead()
                size
            }
            closed -> readAvailableClosed()
            length == 0L -> 0
            else -> readAvailableSuspend(dst, offset, length)
        }
    }

    private suspend fun readAvailableSuspend(dst: CPointer<ByteVar>, offset: Long, length: Long): Int {
        awaitSuspend(1)
        return readAvailable(dst, offset, length)
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
                tryReadCPointer(dst, offset, length)
                afterRead()
            }
            closed -> throw EOFException("Channel is closed and not enough bytes available: required $length but $availableForRead available")
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
            throw EOFException("Channel is closed and not enough bytes available: required $rem but $availableForRead available")
        }
    }

    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Int, length: Int) {
        return writeFully(src, offset.toLong(), length.toLong())
    }

    override suspend fun writeFully(src: CPointer<ByteVar>, offset: Long, length: Long) {
        if (notFull.check()) {
            val size = tryWriteCPointer(src, offset, length).toLong()

            if (length == size) {
                afterWrite()
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
            awaitFreeSpace()
            val size = tryWriteCPointer(src, position, rem).toLong()
            rem -= size
            position += size
            if (rem > 0) flush()
            else afterWrite()
        }
    }

    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Int, length: Int): Int {
        return writeAvailable(src, offset.toLong(), length.toLong())
    }

    override suspend fun writeAvailable(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        if (notFull.check()) {
            val size = tryWriteCPointer(src, offset, length)
            afterWrite()
            return size
        }

        return writeAvailableSuspend(src, offset, length)
    }

    private suspend fun writeAvailableSuspend(src: CPointer<ByteVar>, offset: Long, length: Long): Int {
        awaitFreeSpace()
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
