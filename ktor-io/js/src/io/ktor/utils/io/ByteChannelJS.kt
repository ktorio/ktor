package io.ktor.utils.io

import kotlinx.coroutines.*
import io.ktor.utils.io.internal.*
import io.ktor.utils.io.core.*
import org.khronos.webgl.*

/**
 * Creates buffered channel for asynchronous reading and writing of sequences of bytes.
 */
actual fun ByteChannel(autoFlush: Boolean): ByteChannel {
    return ByteChannelJS(IoBuffer.Empty, autoFlush)
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

    return ByteChannelJS(head, false).apply { close() }
}

/**
 * Creates channel for reading from the specified [ArrayBufferView]
 */
fun ByteReadChannel(content: ArrayBufferView): ByteReadChannel {
    if (content.byteLength == 0) return ByteReadChannel.Empty
    val head = IoBuffer.Pool.borrow()
    var tail = head

    var start = 0
    var remaining = content.byteLength - content.byteOffset
    while (true) {
        tail.reserveEndGap(8)
        val size = minOf(remaining, tail.writeRemaining)
        tail.writeFully(content, start, size)
        start += size
        remaining -= size

        if (remaining == 0) break
        tail = IoBuffer.Pool.borrow()
    }

    return ByteChannelJS(head, false).apply { close() }
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

internal class ByteChannelJS(initial: IoBuffer, autoFlush: Boolean) : ByteChannelSequentialBase(initial, autoFlush) {
    private var attachedJob: Job? = null

    @UseExperimental(InternalCoroutinesApi::class)
    override fun attachJob(job: Job) {
        attachedJob?.cancel()
        attachedJob = job
        job.invokeOnCompletion(onCancelling = true) { cause ->
            attachedJob = null
            if (cause != null) {
                cancel(CancellationException("Channel closed due to job failure: $cause"))
            }
        }
    }

    override suspend fun readAvailable(dst: ArrayBuffer, offset: Int, length: Int): Int {
        return if (readable.isEmpty) {
            readAvailableSuspend(dst, offset, length)
        } else {
            closedCause?.let { throw it }
            readable.readAvailable(dst, offset, length)
        }
    }

    private suspend fun readAvailableSuspend(dst: ArrayBuffer, offset: Int, length: Int): Int {
        if (!await(1)) return -1
        return readAvailable(dst, offset, length)
    }

    override suspend fun readFully(dst: ArrayBuffer, offset: Int, length: Int) {
        if (availableForRead >= length) {
            closedCause?.let { throw it }
            readable.readFully(dst, offset, length)
            return
        }

        return readFullySuspend(dst, offset, length)
    }

    private suspend fun readFullySuspend(dst: ArrayBuffer, offset: Int, length: Int) {
        var start = offset
        val end = offset + length
        var remaining = length

        while (start < end) {
            val rc = readAvailable(dst, start, remaining)
            if (rc == -1) throw EOFException("Premature end of stream: required $remaining more bytes")
            start += rc
            remaining -= rc
        }
    }
}
