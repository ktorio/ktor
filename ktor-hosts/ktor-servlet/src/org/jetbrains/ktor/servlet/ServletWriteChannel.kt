package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream, val exec: ExecutorService) : WriteChannel {
    private val listenerInstalled = AtomicBoolean(false)

    @Volatile
    private var currentHandler: Continuation<Unit>? = null

    private var bytesWrittenWithNoSuspend = 0L
    private var heapBuffer: ByteBuffer? = null

    companion object {
        const val MaxChunkWithoutSuspension = 100 * 1024 * 1024 // 100K
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
        if (listenerInstalled.get()) {
            awaitForWriteReady()
            servletOutputStream.flush()
        }
    }

    private suspend fun awaitForWriteReady() {
        if (!listenerInstalled.get() && listenerInstalled.compareAndSet(false, true)) {
            suspendCoroutine<Unit> { continuation ->
                currentHandler = continuation
                servletOutputStream.setWriteListener(writeReadyListener)
            }
        }

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

    override suspend fun write(src: ByteBuffer) {
        awaitForWriteReady()

        val size = servletOutputStream.doWrite(src)
        bytesWrittenWithNoSuspend += size

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
        suspendCoroutineOrReturn<Unit> {
            bytesWrittenWithNoSuspend = 0L
            exec.submit {
                it.resume(Unit)
            }
            COROUTINE_SUSPENDED
        }
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