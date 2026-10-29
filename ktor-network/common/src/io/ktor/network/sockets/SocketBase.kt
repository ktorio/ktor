/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.AtomicRef
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.coroutines.CoroutineContext

internal abstract class SocketBase(
    parent: CoroutineContext
) : ReadWriteSocket, SelectableBase(), CoroutineScope {

    private val closeFlag = atomic(false)
    private val actualCloseFlag = atomic(false)

    private val readerJob = atomic<ReaderJob?>(null)
    private val writerJob = atomic<WriterJob?>(null)
    private val activeChannels = atomic(0)

    // Declare the lambdas as class fields because of KTOR-8525
    private val channelCompletionHandler: (Throwable?) -> Unit = {
        activeChannels.decrementAndGet()
        checkChannels()
    }
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
        activeChannels.incrementAndGet()
        val j = try {
            if (closeFlag.value) {
                val e = IOException("Socket closed")
                channel.close(e)
                throw e
            }

            producer(channel)
        } catch (cause: Throwable) {
            activeChannels.decrementAndGet()
            checkChannels()
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

    private fun checkChannels() {
        if (closeFlag.value && activeChannels.value == 0) {
            if (!actualCloseFlag.compareAndSet(expect = false, update = true)) return

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
