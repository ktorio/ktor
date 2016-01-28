package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.*

class StatefulAsyncFileChannel (val fc: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = fc.size() - 1) : AsynchronousByteChannel {
    private var position = start

    override fun close() = fc.close()
    override fun isOpen() = fc.isOpen

    override fun <A> write(p0: ByteBuffer?, p1: A, p2: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(p0: ByteBuffer?): Future<Int>? {
        throw UnsupportedOperationException()
    }

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (start > endInclusive) {
            handler.completed(-1, attachment)
            return
        }

        fc.read(dst, position, attachment, object: CompletionHandler<Int, A> {
            override fun failed(exc: Throwable?, attachment: A) {
                handler.failed(exc, attachment)
            }

            override fun completed(rc: Int, attachment: A) {
                if (rc == -1) {
                    handler.completed(-1, attachment)
                } else {
                    position += rc
                    val overRead = Math.max(0L, position - endInclusive - 1)
                    if (overRead > 0) {
                        require(overRead < Int.MAX_VALUE)
                        dst.position(dst.position() - overRead.toInt())
                    }
                    handler.completed(rc - overRead.toInt(), attachment)
                }
            }
        })
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val f = CompletableFuture<Int>()

        read(dst, Unit, object: CompletionHandler<Int, Unit> {
            override fun failed(exc: Throwable?, attachment: Unit) {
                f.completeExceptionally(exc)
            }

            override fun completed(rc: Int, attachment: Unit) {
                f.complete(rc)
            }
        })

        return f
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1) = StatefulAsyncFileChannel(AsynchronousFileChannel.open(this, StandardOpenOption.READ), start, endInclusive)
fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1) = toPath().asyncReadOnlyFileChannel(start, endInclusive)
