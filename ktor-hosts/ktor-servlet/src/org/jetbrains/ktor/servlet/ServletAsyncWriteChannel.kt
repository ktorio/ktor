package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.atomic.*
import javax.servlet.*

internal class ServletAsyncWriteChannel(val servletOutputStream: ServletOutputStream) : AsyncWriteChannel {
    private val listenerInstalled = AtomicBoolean()
    private val currentHandler = AtomicReference<AsyncHandler?>()

    @Volatile
    private var currentBuffer: ByteBuffer? = null

    @Volatile
    private var lastWriteSize: Int = 0

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            val handler = currentHandler.get()
            if (handler != null) {
                val buffer = currentBuffer

                if (buffer == null) {
                    currentHandler.set(null)
                    handler.success(lastWriteSize)
                } else {
                    doWrite(buffer, handler)
                }
            }
        }

        override fun onError(t: Throwable) {
            currentBuffer = null
            currentHandler.getAndSet(null)?.let { handler ->
                handler.failed(t)
            }
        }
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!currentHandler.compareAndSet(null, handler)) {
            handler.failed(IllegalStateException("Write operation is already in progress"))
            return
        }
        currentBuffer = src

        if (listenerInstalled.compareAndSet(false, true)) {
            servletOutputStream.setWriteListener(writeReadyListener)
        } else {
            doWrite(src, handler)
        }
    }

    private fun doWrite(buffer: ByteBuffer, handler: AsyncHandler) {
        if (servletOutputStream.isReady) {
            try {
                doWrite(buffer)

                currentBuffer = null
                currentHandler.compareAndSet(handler, null)

                handler.success(lastWriteSize)
            } catch (t: Throwable) {
                currentBuffer = null
                currentHandler.compareAndSet(handler, null)

                handler.failed(t)
            }
        }
    }

    private fun doWrite(buffer: ByteBuffer) {
        lastWriteSize = buffer.remaining()
        if (buffer.hasArray()) {
            servletOutputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            buffer.position(buffer.limit())
        } else {
            val heapBuffer = ByteBuffer.allocate(buffer.remaining())
            heapBuffer.put(buffer)
            doWrite(heapBuffer)
        }
    }

    override fun close() {

    }
}