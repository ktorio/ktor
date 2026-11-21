/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.loop
import kotlinx.atomicfu.update
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext

internal abstract class SocketBase(
    parent: CoroutineContext
) : ReadWriteSocket, SelectableBase(), CoroutineScope {

    private val closeFlag = atomic(false)

    private val readerJob = atomic<ReaderJob?>(null)
    private val writerJob = atomic<WriterJob?>(null)

    // Channel coroutines and calls using the descriptor, or DESCRIPTOR_CLOSED once actualClose() was called.
    private val activeChannels = atomic(0)

    // Declare the lambdas as class fields because of KTOR-8525
    private val channelCompletionHandler: (Throwable?) -> Unit = { releaseDescriptor() }
    private val closeCompletionHandler: (Throwable?) -> Unit = { checkChannels() }

    override val socketContext: CompletableJob = Job(parent[Job])

    override val coroutineContext: CoroutineContext
        get() = socketContext

    override fun dispose() {
        close()
    }

    @OptIn(InternalAPI::class)
    override fun close() {
        if (!closeFlag.compareAndSet(expect = false, update = true)) return

        launch(CoroutineName("socket-close")) {
            try {
                readerJob.value?.flushAndClose()
            } finally {
                writerJob.value?.cancel()
            }
        }.invokeOnCompletion(closeCompletionHandler) // Also runs when the socket context is already cancelled
    }

    final override fun attachForReading(channel: ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob, ::attachForReadingImpl)
    }

    final override fun attachForWriting(channel: ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob, ::attachForWritingImpl)
    }

    abstract fun attachForReadingImpl(channel: ByteChannel): WriterJob
    abstract fun attachForWritingImpl(channel: ByteChannel): ReaderJob

    private inline fun <J : ChannelJob> attachFor(
        name: String,
        channel: ByteChannel,
        ref: AtomicRef<J?>,
        producer: (ByteChannel) -> J,
    ): J {
        // Closing must wait for I/O even before its job is published or if the attachment is rejected.
        val j = try {
            if (!acquireDescriptor() || closeFlag.value) {
                val e = IOException("Socket closed")
                channel.close(e)
                throw e
            }

            producer(channel)
        } catch (cause: Throwable) {
            releaseDescriptor()
            throw cause
        }

        j.invokeOnCompletion(channelCompletionHandler)

        if (!ref.compareAndSet(null, j)) {
            val e = IllegalStateException("$name channel has already been set")
            j.cancel()
            throw e
        }
        if (closeFlag.value) {
            val e = IOException("Socket closed")
            j.cancel()
            channel.close(e)
            throw e
        }

        channel.attachJob(j)

        return j
    }

    internal abstract fun actualClose(): Throwable?

    /**
     * Runs [block], a call that uses the descriptor outside the attached channels, such as a datagram send.
     * [close] does not close the descriptor until [block] returns, so [block] must not suspend.
     *
     * @throws IOException if the descriptor is already closed.
     */
    internal inline fun <R> useDescriptor(block: () -> R): R {
        if (!acquireDescriptor()) throw IOException("Socket closed")
        try {
            return block()
        } finally {
            releaseDescriptor()
        }
    }

    /**
     * Counts one more user of the descriptor, or returns `false` without counting if it is already closed.
     * Releasing after a failed call has no effect.
     */
    internal fun acquireDescriptor(): Boolean {
        activeChannels.loop { count ->
            if (count == DESCRIPTOR_CLOSED) return false
            if (activeChannels.compareAndSet(count, count + 1)) return true
        }
    }

    internal fun releaseDescriptor() {
        activeChannels.update { count -> if (count == DESCRIPTOR_CLOSED) count else count - 1 }
        checkChannels()
    }

    private fun checkChannels() {
        if (closeFlag.value && activeChannels.value == 0) {
            if (!activeChannels.compareAndSet(0, DESCRIPTOR_CLOSED)) return

            val e1 = readerJob.exception
            val e2 = writerJob.exception
            val e3 = actualClose()

            val combined = combine(combine(e1, e2), e3)

            if (combined == null) socketContext.complete() else socketContext.completeExceptionally(combined)
        }
    }

    private fun combine(e1: Throwable?, e2: Throwable?): Throwable? = when {
        e1 == null -> e2

        e2 == null -> e1

        e1 === e2 -> e1

        else -> {
            e1.addSuppressed(e2)
            e1
        }
    }

    private inline val AtomicRef<out ChannelJob?>.exception: Throwable?
        get() = value?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause // TODO it should be completable deferred or provide its own exception
}

private const val DESCRIPTOR_CLOSED = -1
