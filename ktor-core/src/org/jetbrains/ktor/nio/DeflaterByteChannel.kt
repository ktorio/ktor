package org.jetbrains.ktor.http

import java.nio.*
import java.nio.channels.*
import java.util.concurrent.*
import java.util.zip.*

class AsyncDeflaterByteChannel(val source: AsynchronousByteChannel) : AsynchronousByteChannel {
    private val GZIP_MAGIC = 0x8b1f
    private val crc = CRC32()
    private val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    private var eos = false
    private val buffer = ByteBuffer.allocate(8192)
    private val compressedBuffer = ByteBuffer.allocate(8192).apply {
        position(limit())
    }

    private val header = ByteBuffer.allocate(10).apply {
        order(ByteOrder.LITTLE_ENDIAN)
        putShort(GZIP_MAGIC.toShort())
        put(Deflater.DEFLATED.toByte())
        clear()
    }
    private val trailing = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
        flip()
    }

    override fun isOpen() = source.isOpen
    override fun close() {
        // TODO flush
        source.close()
    }

    override fun <A : Any?> write(p0: ByteBuffer?, p1: A, p2: CompletionHandler<Int, in A>?) {
        throw UnsupportedOperationException()
    }

    override fun write(p0: ByteBuffer?): Future<Int>? {
        throw UnsupportedOperationException()
    }

    override fun read(dst: ByteBuffer): Future<Int> {
        val future = CompletableFuture<Int>()

        read(dst, Unit, object: CompletionHandler<Int, Unit> {
            override fun completed(result: Int, p1: Unit?) {
                future.complete(result)
            }

            override fun failed(exc: Throwable?, p1: Unit?) {
                future.completeExceptionally(exc)
            }
        })

        return future
    }

    override fun <A> read(dst: ByteBuffer, attachment: A, handler: CompletionHandler<Int, in A>) {
        if (header.hasRemaining()) {
            val size = header.putTo(dst)
            handler.completed(size, attachment)
            return
        }
        if (eos) {
            if (!deflater.finished()) {
                finish()
            }

            val size = compressedBuffer.putTo(dst) + trailing.putTo(dst)
            if (size == 0) {
                handler.completed(-1, attachment)
            } else {
                handler.completed(size, attachment)
            }
            return
        }
        if (dst.remaining() <= compressedBuffer.remaining()) {
            val size = compressedBuffer.putTo(dst)
            handler.completed(size, attachment)
            return
        }

        source.read<A>(buffer, attachment, object: CompletionHandler<Int, A> {
            override fun completed(rc: Int, attachment: A) {
                if (rc == -1) {
                    eos = true

                    deflater.finish()
                    finish()

                    val size = compressedBuffer.putTo(dst)
                    handler.completed(size, attachment)
                } else {
                    require(deflater.finished() == false)

                    buffer.flip()
                    crc.update(buffer.array(), 0, buffer.remaining())
                    deflater.setInput(buffer.array(), 0, buffer.remaining())

                    var counter = 0
                    while (!deflater.needsInput() && dst.hasRemaining()) {
                        compressedBuffer.compact()

                        deflater.deflate(compressedBuffer)
                        compressedBuffer.flip()

                        counter += compressedBuffer.putTo(dst)
                    }

                    handler.completed(counter, attachment)
                }
            }

            override fun failed(exc: Throwable, attachment: A) {
                handler.failed(exc, attachment)
            }
        })
    }

    private fun finish() {
        compressedBuffer.compact()

        while (!deflater.finished() && compressedBuffer.hasRemaining()) {
            deflater.deflate(compressedBuffer)
        }

        prepareTrailer()
        trailing.putTo(compressedBuffer)
        compressedBuffer.flip()
    }

    private fun prepareTrailer() {
        trailing.clear()
        trailing.putInt(crc.value.toInt())
        trailing.putInt(deflater.totalIn)
        trailing.flip()
    }

}

fun AsynchronousByteChannel.deflated() = AsyncDeflaterByteChannel(this)

private fun ByteBuffer.putTo(other: ByteBuffer): Int {
    val size = Math.min(remaining(), other.remaining())
    for (i in 1..size) {
        other.put(get())
    }
    return size
}

private fun Deflater.deflate(outBuffer: ByteBuffer) {
    if (outBuffer.hasRemaining()) {
        val written = deflate(outBuffer.array(), outBuffer.arrayOffset() + outBuffer.position(), outBuffer.remaining())
        outBuffer.position(outBuffer.position() + written)
    }
}
