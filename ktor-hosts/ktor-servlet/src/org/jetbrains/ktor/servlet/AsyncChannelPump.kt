package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.logging.*
import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import javax.servlet.*

/**
 * Transfers content asynchronously from the specified [file] to the [servletOutputStream].
 * Both reading from the file and writing to the output are completely asynchronous.
 *
 * Notice that you should startAsync before use this
 */
internal class AsyncChannelPump(val channel: AsynchronousByteChannel, val asyncContext: AsyncContext, val servletOutputStream: ServletOutputStream, val logger: ApplicationLog): Closeable {
    private val bb = ByteBuffer.allocate(4096)
    private var completed = false

    init {
        bb.position(bb.capacity())
    }

    fun start() {
        servletOutputStream.setWriteListener(object : WriteListener {
            override fun onWritePossible() {
                if (completed) {
                    tryComplete()
                } else if (writeLoop()) {
                    startRead()
                }
            }

            override fun onError(t: Throwable) {
                complete()
            }
        })
        asyncContext.addListener(object: AsyncListener {
            override fun onComplete(p0: AsyncEvent?) {
                channel.close()
            }

            override fun onTimeout(p0: AsyncEvent?) {
                channel.close()
            }

            override fun onStartAsync(p0: AsyncEvent?) {
            }

            override fun onError(p0: AsyncEvent?) {
                channel.close()
            }
        })
    }

    override fun close() {
        channel.close()
    }

    private fun complete() {
        try {
            asyncContext.complete()
        } catch (ignore: Throwable) {
        }
        close()
    }

    private fun writeLoop(): Boolean {
        try {
            while (bb.hasRemaining()) {
                if (!servletOutputStream.isReady) {
                    return false
                }

                val toWrite = bb.remaining()

                servletOutputStream.write(bb.array(), bb.arrayOffset() + bb.position(), toWrite)
                bb.position(bb.position() + toWrite)
            }

            return true
        } catch (e: Throwable) {
            complete()
            return false
        }
    }

    private fun tryComplete() {
        require(completed)

        if (servletOutputStream.isReady) {
            servletOutputStream.flush()
            complete()
        }
    }

    private val channelReadCompletionHandler = object : CompletionHandler<Int, Unit> {
        override fun completed(result: Int, attachment: Unit) {
            if (result == -1) {
                completed = true
                tryComplete()
            } else {
                bb.flip()

                if (writeLoop()) {
                    startRead()
                }
            }
        }

        override fun failed(exc: Throwable, attachment: Unit) {
            if (channel.isOpen) {
                logger.error("Failed to read async channel", exc)
            }
        }
    }

    private fun startRead() {
        bb.compact()
        channel.read(bb, Unit, channelReadCompletionHandler)
    }
}