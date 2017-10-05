package io.ktor.servlet

import kotlinx.coroutines.experimental.channels.*
import io.ktor.cio.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream) : WriteChannel {
    @Volatile
    private var listenerInstalled = 0

    @Volatile
    private var writeInProgress = 0

    private val state = ConflatedChannel<Unit>()

    private var heapBuffer: ByteBuffer? = null

    private val enableSetWriteListenerHack = servletOutputStream.javaClass.name == "org.apache.coyote.http11.upgrade.UpgradeServletOutputStream"

    companion object {
        private val Empty: ByteBuffer = ByteBuffer.allocate(0)
        private val listenerInstalledUpdater = AtomicIntegerFieldUpdater.newUpdater(ServletWriteChannel::class.java, ServletWriteChannel::listenerInstalled.name)
        private val writeInProgressUpdater = AtomicIntegerFieldUpdater.newUpdater(ServletWriteChannel::class.java, ServletWriteChannel::writeInProgress.name)
    }

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            if (servletOutputStream.isReady) {
                state.offer(Unit)
            }
        }

        override fun onError(t: Throwable?) {
            state.close(t?.let { writeException("Failed to write to output stream", t) } ?: ChannelWriteException("Failed to write to output stream", IOException()))
        }
    }

    suspend override fun flush() {
        if (listenerInstalled != 0) {
            try {
                if (servletOutputStream.isReady) {
                    servletOutputStream.flush()
                    return
                }
            } catch (t: Throwable) {
                throw writeException("Failed to flush output stream", t)
            }

            return flushSuspend()
        }
    }

    private suspend fun flushSuspend() {
        try {
            while (!servletOutputStream.isReady) {
                state.receiveOrNull() ?: return
            }

            servletOutputStream.flush()
        } catch (t: Throwable) {
            throw writeException("Failed to flush output stream", t)
        }
    }

    private suspend fun installAndWrite(src: ByteBuffer) {
        servletOutputStream.setWriteListener(writeReadyListener)

        // workaround for a tomcat bug
        try {
            if (enableSetWriteListenerHack && servletOutputStream.isReady) {
                state.offer(Unit)
            }
        } catch (t: Throwable) {
            throw writeException("Failed to apply workaround for Tomcat's bug", t)
        }
        // end of workaround

        return writeSuspendUnconfined(src)
    }

    private suspend fun writeSuspendUnconfined(src: ByteBuffer) {
        return suspendCoroutineOrReturn { cont ->
            bufferForLambda = src
            writeSuspendFunction.startCoroutineUninterceptedOrReturn(EmptyContextContinuationDelegate(cont))
        }

        // ~= return run(Unconfined) { writeSuspend(dst) }
    }

    @Volatile
    private var bufferForLambda: ByteBuffer = Empty

    private val writeSuspendFunction = suspend {
        writeSuspend(bufferForLambda).also { bufferForLambda = Empty }
    }

    private suspend fun writeSuspend(src: ByteBuffer) {
        try {
            tryReceive()

            val written = try {
                if (servletOutputStream.isReady) {
                    servletOutputStream.doWrite(src)
                    true
                } else false
            } catch (t: Throwable) {
                throw writeException("Failed to write to output stream", t)
            }

            if (written) ensureWritten()
            else writeSuspend(src)
        } finally {
            endWriting()
        }
    }

    override suspend fun write(src: ByteBuffer) {
         startWriting()

        if (state.poll() == null && state.isClosedForReceive) throw closedException()
        if (listenerInstalled == 0 && listenerInstalledUpdater.compareAndSet(this, 0, 1)) {
            return installAndWrite(src)
        }

        val written = try {
            if (servletOutputStream.isReady) {
                servletOutputStream.doWrite(src)
                endWriting()
                true
            } else false
        } catch (t: Throwable) {
            throw writeException("Failed to write to output stream", t)
        }

        return if (written) {
            ensureWritten()
        } else writeSuspendUnconfined(src)
    }

    private suspend fun tryReceive() {
        state.receiveOrNull() ?: throw closedException()
    }

    private suspend fun ensureWritten() {
        // it is very important here to wait for isReady again otherwise the buffer we have provided is still in use
        // by the container so we shouldn't use it before (otherwise the content could be corrupted or duplicated)
        // notice that in most cases isReady = true after write as there is already buffer inside of the output
        // so we actually don't need double-buffering here

        val notReady = try {
            !servletOutputStream.isReady
        } catch (t: Throwable) {
            throw writeException("Failed to write to output stream", t)
        }

        if (notReady) tryReceive()
    }

    private fun startWriting() {
        if (!writeInProgressUpdater.compareAndSet(this, 0, 1)) throw IllegalStateException("Write operation is already in progress")
    }

    private fun endWriting() {
        writeInProgress = 0
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun writeException(message: String, t: Throwable) = if (t is IOException && t !is ChannelWriteException) ChannelWriteException(message, t) else t

    @Suppress("NOTHING_TO_INLINE")
    private inline fun closedException() = ChannelWriteException("Write failed because the channel has been closed", ClosedChannelException())

    private fun ServletOutputStream.doWrite(src: ByteBuffer): Int {
        val size = src.remaining()

        if (src.hasArray()) {
            write0(src, size)
        } else {
            val copy = heapBuffer?.takeIf { it.capacity() >= size } ?: ByteBuffer.allocate(size)!!.also { heapBuffer = it }
            copy.clear()
            copy.put(src)

            write0(copy, size)
        }
        return size
    }

    private fun ServletOutputStream.write0(src: ByteBuffer, size: Int) {
        write(src.array(), src.arrayOffset() + src.position(), size)
        src.position(src.position() + size)
    }

    override fun close() {
        state.close()
        try {
            servletOutputStream.close()
        } catch (t: Throwable) {
            throw writeException("Failed to close output stream", t)
        }
    }

    private class EmptyContextContinuationDelegate<T>(cont: Continuation<T>) : Continuation<T> by cont {
        override val context: CoroutineContext get() = EmptyCoroutineContext
    }

    private fun <T> suspend(block: suspend () -> T): suspend () -> T = block

}