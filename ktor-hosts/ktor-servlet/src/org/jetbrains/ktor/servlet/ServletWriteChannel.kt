package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream, val exec: ExecutorService) : WriteChannel {
    private var listenerInstalled = false
    private val currentHandler = AtomicReference<Continuation<Unit>>()
    private var currentBuffer: ByteBuffer? = null
    private val flushRequested = AtomicLong()
    private var bytesWrittenWithNoSuspend = 0L
    private val operationInProgress = AtomicBoolean(false)

    companion object {
        const val MaxChunkWithoutSuspension = 100 * 1024 * 1024 // 100K
    }

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            var continuation: Continuation<Unit>? = null

            operationInProgress.ensureRunOnce {
                if (!tryPending()) {
                    return
                }

                currentBuffer = null
                continuation = currentHandler.getAndSet(null)
            }

            continuation?.resume(Unit)
        }

        override fun onError(t: Throwable?) {
            fireHandler { handler, _ ->
                handler.resumeWithException(t ?: RuntimeException("ServletWriteChannel.onError(null)"))
            }
        }
    }

    suspend override fun flush() {
        return suspendCoroutineOrReturn { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Write/flush operation is pending")
            }

            currentBuffer = null
            flushRequested.incrementAndGet()

            if (!listenerInstalled) {
                listenerInstalled = true
                servletOutputStream.setWriteListener(writeReadyListener)
                return@suspendCoroutineOrReturn COROUTINE_SUSPENDED
            }

            operationInProgress.ensureRunOnce {
                return@suspendCoroutineOrReturn if (tryPending()) {
                    currentHandler.compareAndSet(continuation, null)
                    Unit
                }
                else COROUTINE_SUSPENDED
            }
        }
    }

    override suspend fun write(src: ByteBuffer) {
        if (!src.hasRemaining()) {
            return
        }

        return suspendCoroutineOrReturn { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Write/flush operation is pending")
            }

            currentBuffer = src

            if (!listenerInstalled) {
                listenerInstalled = true
                servletOutputStream.setWriteListener(writeReadyListener)
                return@suspendCoroutineOrReturn COROUTINE_SUSPENDED
            }

            operationInProgress.ensureRunOnce {
                return@suspendCoroutineOrReturn if (tryPending()) {
                    val size = src.remaining()
                    currentBuffer = null
                    currentHandler.compareAndSet(continuation, null)

                    bytesWrittenWithNoSuspend += size
                    if (bytesWrittenWithNoSuspend > MaxChunkWithoutSuspension) {
                        future (exec.toCoroutineDispatcher()) {
                            bytesWrittenWithNoSuspend = 0L
                            continuation.resume(Unit)
                        }
                        COROUTINE_SUSPENDED
                    } else {
                        Unit
                    }
                } else {
                    bytesWrittenWithNoSuspend = 0L
                    COROUTINE_SUSPENDED
                }
            }
        }
    }

    private fun tryPending(): Boolean {
        currentBuffer?.let { buffer ->
            if (!tryWrite(buffer))
                return false
        }
        while (flushRequested.getAndSet(0L) > 0L) {
            if (!tryFlush()) {
                return false
            }
        }

        return true
    }

    private fun tryWrite(src: ByteBuffer): Boolean {
        if (servletOutputStream.isReady) {
            servletOutputStream.doWrite(src)
            return servletOutputStream.isReady
        }

        return false
    }

    private fun tryFlush(): Boolean {
        if (servletOutputStream.isReady) {
            servletOutputStream.flush()
            return true
        }

        return false
    }

    private fun ServletOutputStream.doWrite(src: ByteBuffer) {
        val size = src.remaining()

        if (src.hasArray()) {
            write(src.array(), src.arrayOffset() + src.position(), size)
            src.position(src.position() + size)
        } else {
            val copy = ByteBuffer.allocate(size)
            copy.put(src)
            doWrite(copy)
        }
    }

    override fun close() {
        try {
            servletOutputStream.close()
        } finally {
            fireHandler { handler, _ ->
                handler.resumeWithException(ClosedChannelException())
            }
        }
    }

    private inline fun fireHandler(block: (Continuation<Unit>, ByteBuffer?) -> Unit) {
        val buffer = currentBuffer

        currentHandler.getAndSet(null)?.let { handler ->
            currentBuffer = null

            block(handler, buffer)
        }
    }

    private inline fun AtomicBoolean.ensureRunOnce(block: () -> Unit) {
        if (compareAndSet(false, true)) {
            try {
                block()
            } finally {
                set(false)
            }
        }
    }
}