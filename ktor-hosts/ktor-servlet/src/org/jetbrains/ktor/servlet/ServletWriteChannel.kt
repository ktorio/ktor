package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
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
    private var currentHandler: Continuation<Unit>? = null

    private var bytesWrittenWithNoSuspend = 0L
    private var heapBuffer: ByteBuffer? = null

    companion object {
        const val MaxChunkWithoutSuspension = 100 * 1024 * 1024 // 100K
        private val listenerInstalledUpdater = AtomicIntegerFieldUpdater.newUpdater(ServletWriteChannel::class.java, "listenerInstalled")
    }

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            currentHandler?.let { continuation ->
                if (servletOutputStream.isReady) {
                    currentHandler = null
                    continuation.resume(Unit)
                }
            }
        }

        override fun onError(t: Throwable?) {
            currentHandler?.let { continuation ->
                currentHandler = null
                continuation.resumeWithException(t ?: RuntimeException("ServletWriteChannel.onError(null)"))
            }
        }
    }

    suspend override fun flush() {
        if (listenerInstalled != 0) {
            awaitForListenerInstalled()
            awaitForWriteReady()
            servletOutputStream.flush()
        }
    }

    private suspend fun awaitForWriteReady() {
        while (!servletOutputStream.isReady) {
            suspendCoroutineOrReturn<Unit> { continuation ->
                currentHandler = continuation

                if (servletOutputStream.isReady) {
                    currentHandler = null
                    Unit
                } else {
                    bytesWrittenWithNoSuspend = 0L
                    COROUTINE_SUSPENDED
                }
            }
        }
    }

    private suspend fun awaitForListenerInstalled() {
        if (listenerInstalled == 0 && listenerInstalledUpdater.compareAndSet(this, 0, 1)) {
            suspendCoroutine<Unit> { continuation ->
                currentHandler = continuation
                servletOutputStream.setWriteListener(writeReadyListener)
            }
        }
    }

    override suspend fun write(src: ByteBuffer) {
        awaitForListenerInstalled()
        awaitForWriteReady()

        val size = servletOutputStream.doWrite(src)
        bytesWrittenWithNoSuspend += size
        awaitForWriteReady()
        // it is very important here to wait for isReady again otherwise the buffer we have provided is still in use
        // by the container so we shouldn't use it before (otherwise the content could be corrupted or duplicated)
        // notice that in most cases isReady = true after write as there is already buffer inside of the output
        // so we actually don't need double-buffering here

        if (bytesWrittenWithNoSuspend > MaxChunkWithoutSuspension) {
            forceReschedule()
        }
    }

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

    /**
     * always suspends and resumes later on a pool. Useful to make thread free for other jobs.
     */
    private suspend fun forceReschedule() {
        bytesWrittenWithNoSuspend = 0L
        yield()
    }

    override fun close() {
        try {
            servletOutputStream.close()
        } finally {
            currentHandler?.let { continuation ->
                currentHandler = null
                continuation.resumeWithException(ClosedChannelException())
            }
        }
    }
}