package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream) : WriteChannel {
    private val listenerInstalled = AtomicBoolean()

    private val currentHandler = AtomicReference<Continuation<Unit>>()
    private val currentBuffer = AtomicReference<ByteBuffer?>()

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            fireHandler { continuation, buffer ->
                if (buffer != null && tryWrite(buffer)) {
                    continuation.resume(Unit)
                } else {
                    // onWritePossible or onError will be called again later once write operation complete so we have to restore handler and buffer references
                    // since we are on an event thread it is safe to set handler reference to null and then restore it back
                    if (currentHandler.compareAndSet(null, continuation)) {
                        currentBuffer.set(buffer)
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

            currentBuffer.set(src)

            if (listenerInstalled.compareAndSet(false, true)) {
                servletOutputStream.setWriteListener(writeReadyListener)
                COROUTINE_SUSPENDED
            } else if (tryWrite(src)) {
                currentBuffer.set(null)
                currentHandler.compareAndSet(continuation, null)
                Unit
            } else {
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
        val buffer = currentBuffer.get()

        currentHandler.getAndSet(null)?.let { handler ->
            currentBuffer.compareAndSet(buffer, null)

            block(handler, buffer)
        }
    }
}