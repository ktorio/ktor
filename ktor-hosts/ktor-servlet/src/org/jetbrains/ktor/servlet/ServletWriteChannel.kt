package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.cio.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.*

internal class ServletWriteChannel(val servletOutputStream: ServletOutputStream) : WriteChannel {
    private val listenerInstalled = AtomicBoolean()
    private val writeSemaphore = Semaphore(1)

    override suspend fun write(src: ByteBuffer) {

        while (src.hasRemaining()) {
            val remaining = src.remaining()
            servletOutputStream.write(src.array(), src.arrayOffset() + src.position(), remaining)
            src.position(src.position() + remaining)
        }
    }


    private fun setupOrWrite() {
        if (listenerInstalled.compareAndSet(false, true)) {
            servletOutputStream.setWriteListener(writeReadyListener)
        } else {
            writeAndNotify()
        }
    }

    override fun close() {
        try {
            servletOutputStream.close()
        } finally {
            fireHandler { handler, byteBuffer ->
                handler.failed(ClosedChannelException())
            }
        }
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

    private inline fun fireHandler(block: (AsyncHandler, ByteBuffer?) -> Unit) {
        val buffer = currentBuffer.get()

        currentHandler.getAndSet(null)?.let { handler ->
            currentBuffer.compareAndSet(buffer, null)

            block(handler, buffer)
        }
    }
}