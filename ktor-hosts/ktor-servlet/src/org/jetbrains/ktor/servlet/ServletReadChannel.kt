package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

class ServletReadChannel(val servletInputStream: ServletInputStream) : ReadChannel {
    private val listenerInstalled = AtomicBoolean()
    private val currentHandler = AtomicReference<Continuation<Int>?>()
    private var currentBuffer: ByteBuffer? = null
    @Volatile
    private var closed = false

    private val readListener = object : ReadListener {
        override fun onAllDataRead() {
            withHandler { handler, _ ->
                closed = true
                handler.resume(-1)
            }
        }

        override fun onError(t: Throwable) {
            withHandler { handler, _ ->
                handler.resumeWithException(t)
            }
        }

        override fun onDataAvailable() {
            withHandler { handler, buffer ->
                val result = doRead(buffer)
                if (result is Int) {
                    handler.resume(result)
                } else {
                    handler.resumeWithException(IllegalStateException("data is available but we couldn't read it"))
                }
            }
        }
    }

    suspend override fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining()) {
            return 0
        }

        if (closed) {
            return -1
        }

        return suspendCoroutineOrReturn { continuation ->
            if (!currentHandler.compareAndSet(null, continuation)) {
                throw IllegalStateException("Read operation is already in progress")
            }

            currentBuffer = dst
            if (listenerInstalled.compareAndSet(false, true)) {
                servletInputStream.setReadListener(readListener)
                COROUTINE_SUSPENDED
            } else {
                doRead(dst)
            }
        }
    }

    override fun close() {
        try {
            listenerInstalled.set(true)
            closed = true
            servletInputStream.close()
        } finally {
            closed = true

            withHandler { handler, _ ->
                handler.resumeWithException(ClosedChannelException())
            }
        }
    }

    private fun doRead(buffer: ByteBuffer): Any {
        if (servletInputStream.isFinished) {
            clearReferences()
            return -1
        } else if (servletInputStream.isReady) {
            clearReferences()
            return servletInputStream.read(buffer)
        } else {
            return COROUTINE_SUSPENDED
        }
    }

    private fun ServletInputStream.read(buffer: ByteBuffer): Int {
        if (buffer.hasArray()) {
            val size = read(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            if (size > 0) {
                buffer.position(buffer.position() + size)
            }
            return size
        } else {
            val tempArray = ByteArray(buffer.remaining())
            val size = read(tempArray)
            if (size > 0) {
                buffer.put(tempArray, 0, size)
            }
            return size
        }
    }

    private inline fun withHandler(block: (Continuation<Int>, ByteBuffer) -> Unit) {
        currentHandler.getAndSet(null)?.let { handler ->
            currentBuffer?.let { buffer ->
                currentBuffer = null

                block(handler, buffer)
            }
        }
    }

    private fun clearReferences() {
        currentHandler.set(null)
        currentBuffer = null
    }
}