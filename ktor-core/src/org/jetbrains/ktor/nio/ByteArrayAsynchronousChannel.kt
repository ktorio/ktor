package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*

class ByteArrayAsynchronousChannel(val bb: ByteBuffer, val maxReadAmount: Int = Int.MAX_VALUE) : AsynchronousByteChannel {
    constructor(array: ByteArray, maxReadAmount: Int = Int.MAX_VALUE) : this(ByteBuffer.wrap(array), maxReadAmount)

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

        val size = bb.putTo(dst, maxReadAmount)
        handler.completed(size, attachment)
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val future = CompletableFuture<Int>()
        read(dst, Unit, FutureCompletionHandler(future))
        return future
    }
}
