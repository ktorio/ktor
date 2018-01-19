package io.ktor.network.sockets

import io.ktor.network.selector.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.ByteChannel
import kotlinx.io.pool.*
import java.nio.channels.*
import java.util.concurrent.atomic.*

internal abstract class NIOSocketImpl<out S>(override val channel: S, val selector: SelectorManager, val pool: ObjectPool<ByteBuffer>?) : ReadWriteSocket, SelectableBase(channel)
        where S : java.nio.channels.ByteChannel, S : java.nio.channels.SelectableChannel {

    protected val closeFlag = AtomicBoolean()
    private val readerJob = AtomicReference<ReaderJob?>()
    private val writerJob = AtomicReference<WriterJob?>()

    override val socketContext = CompletableDeferred<Unit>()

    // NOTE: it is important here to use different versions of attachForReadingImpl
    // because it is not always valid to use channel's internal buffer for NIO read/write:
    //  at least UDP datagram reading MUST use bigger byte buffer otherwise datagram could be truncated
    //  that will cause broken data
    // however it is not the case for attachForWriting this is why we use direct writing in any case

    final override fun attachForReading(channel: ByteChannel): WriterJob {
        return attachFor("reading", channel, writerJob) {
            if (pool != null) {
                attachForReadingImpl(channel, this.channel, this, selector, pool, socketContext)
            } else {
                attachForReadingDirectImpl(channel, this.channel, this, selector, socketContext)
            }
        }
    }

    final override fun attachForWriting(channel: ByteChannel): ReaderJob {
        return attachFor("writing", channel, readerJob) {
            attachForWritingDirectImpl(channel, this.channel, this, selector, socketContext)
        }
    }

    override fun dispose() {
        close()
    }

    override fun close() {
        if (closeFlag.compareAndSet(false, true)) {
            readerJob.get()?.channel?.close()
            writerJob.get()?.cancel()
            checkChannels()
        }
    }

    private fun <J : Job> attachFor(name: String, channel: ByteChannel, ref: AtomicReference<J?>, producer: () -> J): J {
        if (closeFlag.get()) {
            val e = ClosedChannelException()
            channel.close(e)
            throw e
        }

        val j = producer()

        if (!ref.compareAndSet(null, j)) {
            val e = IllegalStateException("$name channel has been already set")
            j.cancel(e)
            throw e
        }
        if (closeFlag.get()) {
            val e = ClosedChannelException()
            j.cancel(e)
            channel.close(e)
            throw e
        }

        j.invokeOnCompletion {
            checkChannels()
        }

        return j
    }

    private fun actualClose(): Throwable? {
        return try {
            channel.close()
            super.close()
            socketContext.complete(Unit)
            null
        } catch (t: Throwable) {
            t
        } finally {
            selector.notifyClosed(this)
        }
    }

    private fun checkChannels() {
        if (closeFlag.get() && readerJob.completedOrNotStarted && writerJob.completedOrNotStarted) {
            val e1 = readerJob.exception
            val e2 = writerJob.exception
            val e3 = actualClose()

            val combined = combine(combine(e1, e2), e3)

            if (combined == null) socketContext.complete(Unit) else socketContext.completeExceptionally(combined)
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

    private val AtomicReference<out Job?>.completedOrNotStarted: Boolean
        get() = get().let { it == null || it.isCompleted }

    private val AtomicReference<out Job?>.exception: Throwable?
        get() = get()?.takeUnless { it.isActive || it.isCancelled }
                ?.getCancellationException()
                ?.let { (it as? JobCancellationException)?.cause }
}