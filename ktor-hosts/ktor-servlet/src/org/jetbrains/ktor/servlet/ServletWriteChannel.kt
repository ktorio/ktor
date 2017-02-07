package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream, val exec: ExecutorService) : WriteChannel {
    private var listenerInstalled = false
    private val currentHandler = AtomicReference<Continuation<Unit>>()
    private var currentBuffer: ByteBuffer? = null
    private var bytesWrittenWithNoSuspend = 0L

    companion object {
        const val MaxChunkWithoutSuspension = 100 * 1024 * 1024 // 100K
    }

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            fireHandler { continuation, buffer ->
                if (buffer != null && tryWrite(buffer)) {
                    continuation.resume(Unit)
                } else {
                    // onWritePossible or onError will be called again later once write operation complete so we have to restore handler and buffer references
                    // since we are on an event thread it is safe to set handler reference to null and then restore it back
                    if (currentHandler.compareAndSet(null, continuation)) {
                        currentBuffer = buffer
                    } else {
                        continuation.resumeWithException(ConcurrentModificationException())
                    }
                }
            }
        }

        override fun onError(t: Throwable?) {
            fireHandler { handler, _ ->
                handler.resumeWithException(t ?: RuntimeException("ServletWriteChannel.onError(null)"))
            }
        }
    }

    override suspend fun write(src: ByteBuffer) {
        if (!src.hasRemaining()) {
            return
        }

        return suspendCoroutineOrReturn { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Write operation is pending")
            }

            currentBuffer = src
            val size = src.remaining()

            if (!listenerInstalled) {
                listenerInstalled = true
                servletOutputStream.setWriteListener(writeReadyListener)
                COROUTINE_SUSPENDED
            } else if (tryWrite(src)) {
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

    private fun tryWrite(src: ByteBuffer): Boolean {
        if (servletOutputStream.isReady) {
            servletOutputStream.doWrite(src)
            return servletOutputStream.isReady
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
}