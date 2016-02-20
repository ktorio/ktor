package org.jetbrains.ktor.nio

import java.io.*
import java.nio.*
import java.nio.channels.*
import java.nio.file.*
import java.util.concurrent.*

class StatefulAsyncFileChannel (val fc: AsynchronousFileChannel, val start: Long = 0, val endInclusive: Long = fc.size() - 1, val preventClose: Boolean = false) : AsynchronousByteChannel {

    constructor(fc: AsynchronousFileChannel, range: LongRange = 0L .. fc.size() - 1, preventClose: Boolean = false) : this(fc, range.start, range.endInclusive, preventClose)

    init {
        require(start >= 0L) { "start position shouldn't be negative but it is $start"}
        require(endInclusive >= start) { "endInclusive shouldn't be less than start but start = $start, endInclusive = $endInclusive" }
        require(endInclusive <= fc.size() - 1) { "endInclusive points to the position out of the file: file size = ${fc.size()}, endInclusive = $endInclusive" }
    }

    private var position = start

    val range: LongRange
        get () = start .. endInclusive

    override fun close() {
        if (!preventClose) fc.close()
    }
    override fun isOpen() = fc.isOpen

    override fun <A> write(p0: ByteBuffer?, p1: A, p2: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(p0: ByteBuffer?): Future<Int>? {
        throw UnsupportedOperationException()
    }

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (position > endInclusive) {
            handler.completed(-1, attachment)
            return
        }

        try {
            fc.read(dst, position, attachment, object : CompletionHandler<Int, A> {
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
        } catch (e: Throwable) {
            handler.failed(e, attachment)
        }
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val f = CompletableFuture<Int>()

        read(dst, Unit, FutureCompletionHandler(f))

        return f
    }
}

fun Path.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = Files.size(this) - 1) = StatefulAsyncFileChannel(AsynchronousFileChannel.open(this, StandardOpenOption.READ), start, endInclusive)
fun File.asyncReadOnlyFileChannel(start: Long = 0, endInclusive: Long = length() - 1) = toPath().asyncReadOnlyFileChannel(start, endInclusive)
