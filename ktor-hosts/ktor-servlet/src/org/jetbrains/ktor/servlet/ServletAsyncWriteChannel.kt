package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.nio.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*

internal class ServletAsyncWriteChannel(val servletOutputStream: ServletOutputStream) : AsyncWriteChannel {
    private val listenerInstalled = AtomicBoolean()
    private val currentHandler = AtomicReference<AsyncHandler?>()
    private val pendingFlush = AtomicInteger(0)
    private val writeSemaphore = Semaphore(1)

    @Volatile
    private var currentBuffer: ByteBuffer? = null

    @Volatile
    private var lastWriteSize: Int = 0

    private val writeReadyListener = object : WriteListener {
        override fun onWritePossible() {
            writeAndNotify()
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
            writeAndNotify()
        }
    }

    override fun requestFlush() {
        pendingFlush.incrementAndGet()
        writeAndNotify()
    }

    private fun writeAndNotify() {
        val done = try {
            doPendingWriteFlush()
        } catch (e: Exception) {
            currentBuffer = null
            currentHandler.getAndSet(null)?.failed(e)

            false
        }

        if (done) {
            currentBuffer = null
            currentHandler.getAndSet(null)?.success(lastWriteSize)
        }
    }

    private fun doPendingWriteFlush(): Boolean {
        var writeRequested = false
        var written = false

        var flushRequested = false
        var flushed = false

        writeSemaphore.tryUse {
            val handler = currentHandler.get()
            val buffer = this.currentBuffer

            if (handler != null && buffer != null) {
                writeRequested = true
                written = doWrite(buffer)
                if (!written) {
                    return false
                }
            }

            if (pendingFlush.getAndSet(0) > 0) {
                flushRequested = true
                flushed = doFlush()
                if (!flushed) {
                    pendingFlush.incrementAndGet()
                }
            }
        }

        return (!writeRequested || written) && (!flushRequested || flushed)
    }

    private fun doWrite(buffer: ByteBuffer): Boolean {
        if (servletOutputStream.isReady) {
            writeBuffer(buffer)
            return true
        }

        return false
    }

    private fun writeBuffer(buffer: ByteBuffer) {
        lastWriteSize = buffer.remaining()
        if (buffer.hasArray()) {
            servletOutputStream.write(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
            buffer.position(buffer.limit())
        } else {
            val heapBuffer = ByteBuffer.allocate(buffer.remaining())
            heapBuffer.put(buffer)
            writeBuffer(heapBuffer)
        }
    }

    private fun doFlush(): Boolean {
        if (servletOutputStream.isReady) {
            servletOutputStream.flush()
            return true
        }
        return false
    }

    override fun close() {

    }

    private inline fun Semaphore.tryUse(permits: Int = 1, block: () -> Unit): Boolean {
        if (tryAcquire(permits)) {
            try {
                block()
            } finally {
                release(permits)
            }
            return true
        }
        return false
    }
}