package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.*

@Suppress("DEPRECATION", "OverridingDeprecatedMember")
public class ByteChannelSequentialJVM(
    initial: ChunkBuffer,
    autoFlush: Boolean
) : ByteChannelSequentialBase(initial, autoFlush) {

    @Volatile
    private var attachedJob: Job? = null

    @OptIn(InternalCoroutinesApi::class)
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

    override suspend fun writeAvailable(src: ByteBuffer): Int {
        val count = tryWriteAvailable(src)
        return when {
            count > 0 -> count
            !src.hasRemaining() -> 0
            else -> writeAvailableSuspend(src)
        }
    }

    private suspend fun writeAvailableSuspend(src: ByteBuffer): Int {
        awaitAtLeastNBytesAvailableForWrite(1)
        return writeAvailable(src)
    }

    override suspend fun writeFully(src: ByteBuffer) {
        tryWriteAvailable(src)
        if (!src.hasRemaining()) return

        writeFullySuspend(src)
    }

    private suspend fun writeFullySuspend(src: ByteBuffer) {
        while (src.hasRemaining()) {
            awaitAtLeastNBytesAvailableForWrite(1)
            val count = tryWriteAvailable(src)
            afterWrite(count)
        }
    }

    private fun tryWriteAvailable(src: ByteBuffer): Int {
        val srcRemaining = src.remaining()
        val availableForWrite = availableForWrite

        val count = when {
            closed -> throw closedCause ?: ClosedSendChannelException("Channel closed for write")
            srcRemaining == 0 -> 0
            srcRemaining <= availableForWrite -> {
                writable.writeFully(src)
                srcRemaining
            }
            availableForWrite == 0 -> 0
            else -> {
                val oldLimit = src.limit()
                src.limit(src.position() + availableForWrite)
                writable.writeFully(src)
                src.limit(oldLimit)
                availableForWrite
            }
        }

        afterWrite(count)
        return count
    }

    override suspend fun readAvailable(dst: ByteBuffer): Int {
        val rc = tryReadAvailable(dst)
        if (rc != 0) return rc
        if (!dst.hasRemaining()) return 0
        return readAvailableSuspend(dst)
    }

    override fun readAvailable(min: Int, block: (ByteBuffer) -> Unit): Int {
        closedCause?.let { throw it }

        if (availableForRead < min) {
            return -1
        }

        prepareFlushedBytes()

        var result: Int
        readable.readDirect(min) {
            val position = it.position()
            block(it)
            result = it.position() - position
        }

        return result
    }

    private suspend fun readAvailableSuspend(dst: ByteBuffer): Int {
        if (!await(1)) return -1
        return readAvailable(dst)
    }

    override suspend fun readFully(dst: ByteBuffer): Int {
        val rc = tryReadAvailable(dst)
        if (rc == -1) throw EOFException("Channel closed")
        if (!dst.hasRemaining()) return rc

        return readFullySuspend(dst, rc)
    }

    private suspend fun readFullySuspend(dst: ByteBuffer, rc0: Int): Int {
        var count = rc0

        while (dst.hasRemaining()) {
            if (!await(1)) throw EOFException("Channel closed")
            val rc = tryReadAvailable(dst)
            if (rc == -1) throw EOFException("Channel closed")
            count += rc
        }

        return count
    }

    private fun tryReadAvailable(dst: ByteBuffer): Int {
        closedCause?.let { throw it }

        if (closed && availableForRead == 0) {
            return -1
        }

        if (!readable.canRead()) {
            prepareFlushedBytes()
        }

        val count = readable.readAvailable(dst)
        afterRead(count)
        return count
    }

    @Deprecated("Use read { } instead.")
    override fun <R> lookAhead(visitor: LookAheadSession.() -> R): R =
        visitor(Session(this))

    @Deprecated("Use read { } instead.")
    override suspend fun <R> lookAheadSuspend(visitor: suspend LookAheadSuspendSession.() -> R): R =
        visitor(Session(this))

    private class Session(private val channel: ByteChannelSequentialJVM) : LookAheadSuspendSession {
        override suspend fun awaitAtLeast(n: Int): Boolean {
            channel.closedCause?.let { throw it }

            return channel.await(n)
        }

        override fun consumed(n: Int) {
            channel.closedCause?.let { throw it }

            channel.discard(n)
        }

        override fun request(skip: Int, atLeast: Int): ByteBuffer? {
            channel.closedCause?.let { throw it }

            if (channel.isClosedForRead) return null

            if (channel.readable.isEmpty) {
                channel.prepareFlushedBytes()
            }

            val head = channel.readable.head
            if (head.readRemaining < skip + atLeast) return null

            val buffer = head.memory.buffer.slice()
            buffer.position(head.readPosition + skip)
            buffer.limit(head.writePosition)
            return buffer
        }
    }

    override suspend fun read(min: Int, consumer: (ByteBuffer) -> Unit) {
        require(min >= 0)

        if (!await(min)) throw EOFException("Channel closed while $min bytes expected")

        readable.readDirect(min) { bb ->
            consumer(bb)
        }
    }

    /**
     * Suspend until the channel has bytes to read or gets closed. Throws exception if the channel was closed with an error.
     */
    override suspend fun awaitContent() {
        await(1)
    }

    override fun writeAvailable(min: Int, block: (ByteBuffer) -> Unit): Int {
        if (closed) {
            throw closedCause ?: ClosedSendChannelException("Channel closed for write")
        }

        if (availableForWrite < min) {
            return 0
        }

        var result: Int
        writable.writeDirect(min) {
            val position = it.position()
            block(it)
            result = it.position() - position
        }

        return result
    }

    override suspend fun write(min: Int, block: (ByteBuffer) -> Unit) {
        if (closed) {
            throw closedCause ?: ClosedSendChannelException("Channel closed for write")
        }

        awaitAtLeastNBytesAvailableForWrite(min)
        val count = writable.writeByteBufferDirect(min) { block(it) }
        afterWrite(count)
    }

    override suspend fun writeWhile(block: (ByteBuffer) -> Boolean) {
        while (true) {
            if (closed) {
                throw closedCause ?: ClosedSendChannelException("Channel closed for write")
            }

            var shouldContinue: Boolean
            awaitAtLeastNBytesAvailableForWrite(1)
            val result = writable.writeByteBufferDirect(1) {
                shouldContinue = block(it)
            }

            afterWrite(result)
            if (!shouldContinue) break
        }
    }
}
