package org.jetbrains.ktor.nio

import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*

open class ChainAsyncByteChannel(val chain: Sequence<() -> AsynchronousByteChannel>) : AsynchronousByteChannel {
    private val iterator = chain.iterator()
    private var current: AsynchronousByteChannel? = null
    private var closed = false

    override fun <A> write(src: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        throw UnsupportedOperationException()
    }

    override fun write(src: ByteBuffer): Future<Int> {
        throw UnsupportedOperationException()
    }

    override final fun isOpen() = !closed
    override final fun close() {
        switchCurrent()
        closed = true
    }

    override final fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        val source = ensureCurrent()
        if (source == null) {
            handler.completed(-1, attachment)
            return
        }

        source.read(dst, attachment, object: CompletionHandler<Int, A> {
            override fun failed(exc: Throwable, attachment: A) {
                handler.failed(exc, attachment)
            }

            override fun completed(result: Int, attachment: A) {
                if (result == -1) {
                    switchCurrent()
                    read(dst, attachment, handler)
                } else {
                    handler.completed(result, attachment)
                }
            }
        })
    }

    override final fun read(dst: ByteBuffer): Future<Int> {
        val future = CompletableFuture<Int>()
        read(dst, Unit, FutureCompletionHandler(future))
        return future
    }

    private fun ensureCurrent(): AsynchronousByteChannel? {
        return when {
            closed -> null
            current != null -> current
            iterator.hasNext() -> {
                current = iterator.next().invoke()
                current
            }
            else -> null
        }
    }

    private fun switchCurrent() {
        current?.close()
        current = null
    }
}