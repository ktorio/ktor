package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.atomic.*
import javax.servlet.*

class ServletAsyncWriteChannel(val asyncContext: AsyncContext, val servletOutputStream: ServletOutputStream) : AsyncWriteChannel {
    private val flushPending = AtomicBoolean()
    private val listenerInstalled = AtomicBoolean()
    @Volatile
    private var currentBuffer: ByteBuffer? = null
    private val currentHandler = AtomicReference<AsyncHandler?>()
    private val previousHandler = AtomicReference<AsyncHandler?>()
    private var previousSize = 0

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            if (previousHandler.get() != null) {
                val size = previousSize
                val handler = previousHandler.get()

                if (handler != null && previousHandler.compareAndSet(handler, null)) {
                    handler.success(size)
                }
            }

            doWriteIfReady()
        }

        override fun onError(t: Throwable) {
            currentHandler.get()?.let { handler ->
                currentHandler.set(null)
                currentBuffer = null

                handler.failed(t)
            }
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (listenerInstalled.compareAndSet(false, true)) {
            servletOutputStream.setWriteListener(writeReadyListener)
        }

        if (!currentHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress")
        }
        currentBuffer = src

        doWriteIfReady()
    }

    private fun doWriteIfReady() {
        if (servletOutputStream.isReady) {
            currentHandler.get()?.let { handler ->
                currentBuffer?.let { buffer ->
                    try {
                        doWrite(buffer, handler)
                    } catch (t: Throwable) {
                        handler.failed(t)
                    } finally {
                        currentBuffer = null
                        currentHandler.set(null)
                    }
                }
            }
        }
    }

    private fun doWrite(buffer: ByteBuffer, handler: AsyncHandler) {
        if (buffer.hasArray()) {
            servletOutputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            buffer.position(buffer.limit())
        } else {
            val heapBuffer = ByteBuffer.allocate(buffer.remaining())
            heapBuffer.put(buffer)
            doWrite(heapBuffer, handler)
        }
    }

    override fun close() {

    }
}