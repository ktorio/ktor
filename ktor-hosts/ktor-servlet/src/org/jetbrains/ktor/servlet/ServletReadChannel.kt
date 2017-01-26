package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.atomic.*
import javax.servlet.*

class ServletReadChannel(val servletInputStream: ServletInputStream) : ReadChannel {
    private val listenerInstalled = AtomicBoolean()
    private val currentHandler = AtomicReference<AsyncHandler?>()
    private var currentBuffer: ByteBuffer? = null
    @Volatile
    private var closed = false

    private val readListener = object : ReadListener {
        override fun onAllDataRead() {
            withHandler { handler, buffer ->
                handler.successEnd()
            }
        }

        override fun onError(t: Throwable) {
            withHandler { handler, buffer ->
                handler.failed(t)
            }
        }

        override fun onDataAvailable() {
            withHandler { handler, buffer ->
                doRead(handler, buffer)
            }
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!dst.hasRemaining()) {
            handler.success(0)
            return
        }
        if (closed) {
            throw IllegalStateException("Read channel is already closed")
        }

        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Read operation is already in progress")
        }
        currentBuffer = dst

        if (listenerInstalled.compareAndSet(false, true)) {
            servletInputStream.setReadListener(readListener)
        } else {
            doRead(handler, dst)
        }
    }

    override fun close() {
        try {
            listenerInstalled.set(true)
            closed = true
            servletInputStream.close()
        } finally {
            closed = true

            withHandler { handler, buffer ->
                handler.failed(ClosedChannelException())
            }
        }
    }

    private fun doRead(handler: AsyncHandler, buffer: ByteBuffer) {
        if (servletInputStream.isFinished) {
            clearReferences()
            handler.successEnd()
        } else if (servletInputStream.isReady) {
            clearReferences()

            val size = servletInputStream.read(buffer)
            if (size == -1) {
                handler.successEnd()
            } else {
                handler.success(size)
            }
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

    private inline fun withHandler(block: (AsyncHandler, ByteBuffer) -> Unit) {
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