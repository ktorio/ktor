package org.jetbrains.ktor.servlet

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
internal class AsyncFilePump(val file: Path, var position: Long, val length: Long, val asyncContext: AsyncContext, val servletOutputStream: ServletOutputStream): Closeable {
    private val fc = AsynchronousFileChannel.open(file, StandardOpenOption.READ)

    private val bb = ByteBuffer.allocate(4096)
    private var completed = false
    private val startPosition = position


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

            override fun onError(t: Throwable?) {
                complete()
                // TODO log error
            }
        })
    }

    override fun close() {
        fc.close()
    }

    private fun complete() {
        asyncContext.complete()
        close()
    }

    private fun writeLoop(): Boolean {
        while (bb.hasRemaining()) {
            if (!servletOutputStream.isReady) {
                return false
            }

            val toWrite = bb.remaining()

            servletOutputStream.write(bb.array(), bb.arrayOffset() + bb.position(), toWrite)
            bb.position(bb.position() + toWrite)
        }

        return true
    }

    private fun tryComplete() {
        require(completed)

        if (servletOutputStream.isReady) {
            servletOutputStream.flush()
            complete()
        }
    }

    private fun startRead() {
        bb.compact()
        if (startPosition + length <= position) {
            completed = true
            tryComplete()
        } else {
            fc.read(bb, position, Unit, object : CompletionHandler<Int, Unit> {
                override fun failed(exc: Throwable, attachment: Unit) {
                    complete()
                    // TODO log error
                }

                override fun completed(result: Int, attachment: Unit) {
                    if (result == -1) {
                        completed = true
                        tryComplete()
                    } else {
                        position += result
                        bb.flip()

                        var overRead = position - startPosition - length
                        if (overRead > 0) {
                            require(overRead < Int.MAX_VALUE)
                            bb.limit(bb.limit() - overRead.toInt())
                        }
                        if (writeLoop()) {
                            startRead()
                        }
                    }
                }
            })
        }
    }
}