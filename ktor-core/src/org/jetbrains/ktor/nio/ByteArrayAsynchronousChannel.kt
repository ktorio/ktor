package org.jetbrains.ktor.nio

import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*

class ByteArrayAsynchronousChannel(val bytes: ByteArray) : AsynchronousByteChannel {
    private val bb = ByteBuffer.wrap(bytes)

    override fun <A : Any?> write(p0: ByteBuffer?, p1: A, p2: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(p0: ByteBuffer?): Future<Int>? {
        throw UnsupportedOperationException()
    }

    override fun isOpen() = true
    override fun close() {
    }

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (!bb.hasRemaining()) {
            handler.completed(-1, attachment)
            return
        }

        val size = Math.min(dst.remaining(), bb.remaining())
        repeat(size) {
            dst.put(bb.get())
        }

        handler.completed(size, attachment)
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val future = CompletableFuture<Int>()
        read(dst, Unit, FutureCompletionHandler(future))
        return future
    }
}
