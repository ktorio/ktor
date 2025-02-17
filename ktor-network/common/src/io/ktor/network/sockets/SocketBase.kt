/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.sockets

import io.ktor.network.selector.*
import io.ktor.utils.io.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.io.*
import kotlin.coroutines.*

internal abstract class SocketBase(
    parent: CoroutineContext
) : ReadWriteSocket, SelectableBase(), CoroutineScope {

    private val closeFlag = atomic(false)
    private val actualCloseFlag = atomic(false)

    private val readerJob = atomic<ReaderJob?>(null)

    private val writerJob = atomic<WriterJob?>(null)

    override val socketContext: CompletableJob = Job(parent[Job])

    override val coroutineContext: CoroutineContext
        get() = socketContext

    override fun dispose() {
        close()
    }

    override fun close() {
        if (!closeFlag.compareAndSet(false, true)) return

        // TODO this can be dangerous if there is another thread writing to this
        readerJob.value?.channel?.close()
        writerJob.value?.cancel()
        checkChannels()
    }

    final override fun attachForReading(channel: ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob) {
            attachForReadingImpl(channel)
        }
    }

    final override fun attachForWriting(channel: ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob) {
            attachForWritingImpl(channel)
        }
    }

    abstract fun attachForReadingImpl(channel: ByteChannel): WriterJob
    abstract fun attachForWritingImpl(channel: ByteChannel): ReaderJob

    private inline fun <J : ChannelJob> attachFor(
        name: String,
        channel: ByteChannel,
        ref: AtomicRef<J?>,
        producer: () -> J
    ): J {
        if (closeFlag.value) {
            val e = IOException("Socket closed")
            channel.close(e)
            throw e
        }

        val j = producer()

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

        j.invokeOnCompletion {
            checkChannels()
        }

        return j
    }

    internal abstract fun actualClose(): Throwable?

    private fun checkChannels() {
        if (closeFlag.value && readerJob.completedOrNotStarted && writerJob.completedOrNotStarted) {
            if (!actualCloseFlag.compareAndSet(false, true)) return

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

    private inline val AtomicRef<out ChannelJob?>.completedOrNotStarted: Boolean
        get() = value.let { it == null || it.isCompleted }

    private inline val AtomicRef<out ChannelJob?>.exception: Throwable?
        get() = value?.takeIf { it.isCancelled }
            ?.getCancellationException()?.cause // TODO it should be completable deferred or provide its own exception
}
