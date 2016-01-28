package org.jetbrains.ktor.nio

import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*

class AsyncBoundChannel(val source: AsynchronousByteChannel, val start: Long = 0, val endInclusive: Long? = null): AsynchronousByteChannel {
    private var position = 0L

    override fun <A : Any?> write(p0: ByteBuffer?, p1: A, p2: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(p0: ByteBuffer?): Future<Int>? {
        throw UnsupportedOperationException()
    }

    override fun isOpen() = source.isOpen
    override fun close() = source.close()

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        val positionBefore = dst.position()

        source.read(dst, Unit, object : CompletionHandler<Int, Unit> {
            override fun completed(result: Int, nothing: Unit) {
                if (result == -1) {
                    handler.completed(-1, attachment)
                } else if (result == 0) {
                    handler.completed(0, attachment)
                } else {
                    val cutLeft = (start - position).coerceIn(0L, result.toLong()).toInt()
                    val cutRight = if (endInclusive == null) 0 else (position + result - endInclusive).coerceIn(0L, (result - cutLeft).toLong()).toInt()
                    val newResult = (result - cutLeft - cutRight).coerceAtLeast(0)

                    if (cutLeft + cutRight < result) {
                        for (i in 0..cutLeft - 1) {
                            dst.put(positionBefore + i, dst.get(positionBefore + cutLeft + i))
                        }
                    }
                    dst.position(dst.position() - cutLeft - cutRight)

                    position += result

                    handler.completed(newResult, attachment)
                }
            }

            override fun failed(exc: Throwable, nothing: Unit) {
                handler.failed(exc, attachment)
            }
        })
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val future = CompletableFuture<Int>()

        read(dst, Unit, object: CompletionHandler<Int, Unit> {
            override fun completed(result: Int, nothing: Unit) {
                future.complete(result)
            }

            override fun failed(exc: Throwable, nothing: Unit) {
                future.completeExceptionally(exc)
            }
        })

        return future
    }
}

fun AsynchronousByteChannel.cut(start: Long = 0, endInclusive: Long? = null) = AsyncBoundChannel(this, start, endInclusive)
