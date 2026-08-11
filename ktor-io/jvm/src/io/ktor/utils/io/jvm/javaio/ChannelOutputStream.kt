/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.io.Buffer
import java.io.OutputStream
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * The number of locally buffered bytes after which [ChannelOutputStream.write] hands the buffer
 * off to the flush job, so data is streamed and backpressure applies even when the caller never
 * invokes `flush()`.
 */
private const val FLUSH_THRESHOLD: Long = CHANNEL_MAX_SIZE.toLong()

/**
 * Dispatcher for [ChannelOutputStream] flush jobs.
 *
 * Threads calling the blocking [OutputStream] methods may park while waiting for the flush job to
 * drain the hand-off queue, so the job must never be scheduled on a dispatcher those callers can
 * occupy (an engine event loop or an exhausted [Dispatchers.IO]). `limitedParallelism` on
 * [Dispatchers.IO] is elastic: its threads are allocated in addition to the default I/O pool limit.
 */
private val FlushDispatcher = Dispatchers.IO.limitedParallelism(64, "ktor-channel-output-stream")

/**
 * An [OutputStream] adapter for a [ByteWriteChannel] that avoids blocking the writing thread.
 *
 * Written bytes are accumulated in a local buffer that is handed off to a background flush job
 * when [flush] is called or [FLUSH_THRESHOLD] is exceeded.
 *
 * Closing this stream does **not** close or cancel [channel]; its lifecycle remains with the caller.
 *
 * @param channel the channel to write to.
 * @param coroutineContext parent context for the flush job, used for structured concurrency:
 * cancelling its [Job] cancels the stream. Any dispatcher in it is ignored — the flush job always
 * runs on a dedicated dispatcher so that it can make progress while caller threads are parked.
 */
@InternalAPI
public class ChannelOutputStream(
    private val channel: ByteWriteChannel,
    coroutineContext: CoroutineContext = EmptyCoroutineContext,
) : OutputStream() {
    private val scope = CoroutineScope(coroutineContext)
    private var buffer = Buffer()
    private var closed = false

    @Volatile
    private var failure: Throwable? = null

    // Small queue size to avoid unbounded memory usage when the flush job is slow to write.
    private val flushes = Channel<Buffer>(
        capacity = 4,
        onUndeliveredElement = { it.clear() },
    )

    private val flushJob = scope.launch(FlushDispatcher) {
        try {
            for (chunk in flushes) {
                chunk.transferTo(channel.writeBuffer)
                channel.flush()
            }
        } catch (cause: Throwable) {
            failure = cause
            flushes.close(cause)
            channel.cancel(cause)
            if (cause is CancellationException) throw cause
        }
    }

    init {
        // Backstop for a job that never ran (e.g. the parent was already cancelled): release parked
        // senders and drop buffers that will never be written. No-op after a normal completion.
        flushJob.invokeOnCompletion { cause ->
            if (cause != null && failure == null) failure = cause
            flushes.close(cause)
            flushes.cancel()
        }
    }

    override fun write(b: Int) {
        ensureOpen()
        buffer.writeByte(b.toByte())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        ensureOpen()
        buffer.write(b, off, off + len)
        if (buffer.size >= FLUSH_THRESHOLD) enqueueBuffer()
    }

    override fun flush() {
        ensureOpen()
        if (buffer.size > 0L) enqueueBuffer()
    }

    override fun close() {
        if (closed) return
        runBlocking {
            closeSuspend()
        }
    }

    /**
     * Suspending variant of [close]: delivers the remaining buffered bytes, awaits completion of
     * the flush job and rethrows the failure, if any, encountered while writing to the channel.
     */
    public suspend fun closeSuspend() {
        if (closed) return
        closed = true
        try {
            if (buffer.size > 0L) {
                flushes.send(buffer)
                buffer = Buffer()
            }
        } finally {
            flushes.close()
            flushJob.join()
        }
        failure?.let { throw it }
    }

    /**
     * Hands the accumulated buffer off to the flush job, blocking only when the queue is full.
     */
    private fun enqueueBuffer() {
        val chunk = buffer
        buffer = Buffer()
        if (flushes.trySend(chunk).isSuccess) return
        try {
            // An empty context, so the parked thread doesn't need its own dispatcher to progress:
            // the flush job drains the queue on FlushDispatcher and resumes the send.
            runBlocking {
                flushes.send(chunk)
            }
        } catch (cause: Throwable) {
            chunk.clear()
            throw cause
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private fun ensureOpen() {
        if (closed) throw ClosedWriteChannelException()
        if (flushes.isClosedForSend || channel.isClosedForWrite) {
            throw channel.closedCause ?: failure ?: ClosedWriteChannelException()
        }
    }
}
