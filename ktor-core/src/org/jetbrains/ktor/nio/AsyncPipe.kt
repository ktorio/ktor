package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*

class AsyncPipe : AsyncReadChannel, AsyncWriteChannel {
    private val closed = AtomicBoolean()

    @Volatile
    private var producerBuffer: ByteBuffer? = null
    private val producerHandler = AtomicReference<AsyncHandler?>()
    @Volatile
    private var producerSize: Int = 0

    @Volatile
    private var consumerBuffer: ByteBuffer? = null
    private val consumerHandler = AtomicReference<AsyncHandler?>()

    private val bufferCounter = Semaphore(0)
    private val flushCounter = AtomicInteger()

    private val flushHandler = AtomicReference<AsyncHandler?>()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            closeConsumer()
            closeProducer()
        }
    }

    override fun flush(handler: AsyncHandler) {
        if (closed.get()) {
            handler.successEnd()
            return
        }
        if (!flushHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("flush operation is already in progress")
        }

        requestFlush()
    }

    fun closeAndWait() {
        val latch = BlockingAdapter()
        flush(latch.handler)
        close()
        latch.await()
    }

    private fun closeConsumer() {
        consumerBuffer = null
        consumerHandler.getAndSet(null)?.let { handler ->
            nofail {
                handler.successEnd()
            }
        }
    }

    private fun closeProducer() {
        producerBuffer = null
        producerHandler.getAndSet(null)?.let { handler ->
            nofail {
                handler.failed(PipeClosedException())
            }
        }
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (!consumerHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Read operation is already in progress")
        }
        if (!dst.hasRemaining()) {
            consumerHandler.set(null)
            handler.success(0)
            return
        }
        if (closed.get()) {
            consumerHandler.set(null)
            handler.successEnd()
            return
        }

        consumerBuffer = dst

        bufferCounter.release()

        tryCommunicate()
    }

    override fun write(src: ByteBuffer, handler: AsyncHandler) {
        if (!producerHandler.compareAndSet(null, handler)) {
            throw IllegalStateException("Write operation is already in progress")
        }
        if (!src.hasRemaining()) {
            producerHandler.set(null)
            handler.success(0)
            return
        }
        if (closed.get()) {
            producerHandler.set(null)
            handler.failed(PipeClosedException())
            return
        }

        producerSize = src.remaining()
        producerBuffer = src

        bufferCounter.release()

        tryCommunicate()
    }

    override fun releaseFlush(): Int {
        val counter = flushCounter.getAndSet(0)

        if (counter > 0) {
            flushHandler.getAndSet(null)?.successEnd()
        }

        return counter
    }
    override fun requestFlush() {
        flushCounter.incrementAndGet()
    }

    private fun tryCommunicate() {
        if (bufferCounter.tryAcquire(2)) {
            communicate()
        }
    }

    private fun communicate() {
        producerHandler.get()?.let { producerHandler ->
            consumerHandler.get()?.let { consumerHandler ->
                val producerBuffer = producerBuffer!!
                val consumerBuffer = consumerBuffer!!

                val size = producerBuffer.putTo(consumerBuffer)

                this.consumerBuffer = null
                this.consumerHandler.set(null)

                if (!producerBuffer.hasRemaining()) {
                    this.producerHandler.set(null)
                    this.producerBuffer = null

                    nofail {
                        producerHandler.success(producerSize)
                    }
                } else {
                    bufferCounter.release()
                }

                consumerHandler.success(size)
            }
        }
    }

    private inline fun nofail(block: () -> Unit) {
        try {
            block()
        } catch (ignore: Throwable) {
        }
    }

    class PipeClosedException : EOFException("Pipe closed")
}