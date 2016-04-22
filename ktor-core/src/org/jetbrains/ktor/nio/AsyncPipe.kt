package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.io.*
import java.nio.*
import java.util.concurrent.atomic.*

class AsyncPipe : AsyncReadChannel, AsyncWriteChannel {
    private val closed = AtomicBoolean()

    private var producerBuffer: ByteBuffer? = null
    private val producerHandler = AtomicReference<AsyncHandler?>()
    private var producerSize: Int = 0

    private var consumerBuffer: ByteBuffer? = null
    private val consumerHandler = AtomicReference<AsyncHandler>()

    private val communicateFlag = AtomicBoolean()

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            consumerBuffer = null
            consumerHandler.getAndSet(null)?.let { handler ->
                handler.successEnd()
            }

            producerBuffer = null
            producerHandler.getAndSet(null)?.let { handler ->
                handler.failed(EOFException("Pipe closed"))
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

        communicate()
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
            handler.failed(EOFException("Pipe closed"))
            return
        }

        producerBuffer = src
        producerSize = src.remaining()

        communicate()
    }

    private fun communicate() {
        producerHandler.get()?.let { producerHandler ->
            consumerHandler.get()?.let { consumerHandler ->
                val producerBuffer = producerBuffer!!
                val consumerBuffer = consumerBuffer!!

                val size = if (communicateFlag.compareAndSet(false, true)) {
                    val size = producerBuffer.putTo(consumerBuffer)
                    communicateFlag.set(false)
                    size
                } else 0

                if (size > 0) {
                    this.consumerBuffer = null
                    this.consumerHandler.set(null)

                    if (!producerBuffer.hasRemaining()) {
                        this.producerHandler.set(null)
                        this.producerBuffer = null

                        producerHandler.success(producerSize)
                    }

                    consumerHandler.success(size)
                }
            }
        }
    }
}