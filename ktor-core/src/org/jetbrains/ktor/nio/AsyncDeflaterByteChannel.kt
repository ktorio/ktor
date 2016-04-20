package org.jetbrains.ktor.nio

import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.zip.*

private class AsyncDeflaterByteChannel(val source: AsyncReadChannel, val gzip: Boolean = true) : AsyncReadChannel {
    private val GZIP_MAGIC = 0x8b1f
    private val crc = CRC32()
    private val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    private var eos = false
    private val buffer = ByteBuffer.allocate(8192)
    private val compressedBuffer = ByteBuffer.allocate(8192).apply {
        position(limit())
    }

    private val header = ByteBuffer.allocate(10).apply {
        if (gzip) {
            order(ByteOrder.LITTLE_ENDIAN)
            putShort(GZIP_MAGIC.toShort())
            put(Deflater.DEFLATED.toByte())
            clear()
        } else {
            flip()
        }
    }
    private val trailing = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
        flip()
    }

    override fun close() {
        source.close()
        parentHandler = null
        parentBuffer = null
    }

    override fun read(dst: ByteBuffer, handler: AsyncHandler) {
        if (header.hasRemaining()) {
            val size = header.putTo(dst)
            handler.success(size)
            return
        }
        if (eos) {
            if (!deflater.finished()) {
                finish()
            }

            val size = compressedBuffer.putTo(dst) + trailing.putTo(dst)
            if (size == 0) {
                handler.successEnd()
            } else {
                handler.success(size)
            }
            return
        }
        if (dst.remaining() <= compressedBuffer.remaining()) {
            val size = compressedBuffer.putTo(dst)
            handler.success(size)
            return
        }

        parentBuffer = dst
        parentHandler = handler

        source.read(buffer, innerHandler)
    }

    private val innerHandler = object : AsyncHandler {
        override fun success(count: Int) {
            require(deflater.finished() == false)

            buffer.flip()
            crc.update(buffer.array(), 0, buffer.remaining())
            deflater.setInput(buffer.array(), 0, buffer.remaining())

            withParentHandler { dst, handler ->
                var counter = 0
                while (!deflater.needsInput() && dst.hasRemaining()) {
                    compressedBuffer.compact()

                    deflater.deflate(compressedBuffer)
                    compressedBuffer.flip()

                    counter += compressedBuffer.putTo(dst)
                }

                handler.success(counter)
            }
        }

        override fun successEnd() {
            eos = true

            deflater.finish()
            finish()

            withParentHandler { dst, handler ->
                val size = compressedBuffer.putTo(dst)
                handler.success(size)
            }
        }

        override fun failed(cause: Throwable) {
            withParentHandler { dst, handler ->
                handler.failed(cause)
            }
        }
    }

    @Volatile
    private var parentHandler: AsyncHandler? = null

    @Volatile
    private var parentBuffer: ByteBuffer? = null

    private inline fun withParentHandler(block: (ByteBuffer, AsyncHandler) -> Unit) {
        val buffer = parentBuffer
        val handler = parentHandler
        parentBuffer = null
        parentHandler = null

        block(buffer!!, handler!!)
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
        if (gzip) {
            trailing.clear()
            trailing.putInt(crc.value.toInt())
            trailing.putInt(deflater.totalIn)
            trailing.flip()
        }
    }

}

fun AsyncReadChannel.deflated(gzip: Boolean = true): AsyncReadChannel = AsyncDeflaterByteChannel(this, gzip)

private fun Deflater.deflate(outBuffer: ByteBuffer) {
    if (outBuffer.hasRemaining()) {
        val written = deflate(outBuffer.array(), outBuffer.arrayOffset() + outBuffer.position(), outBuffer.remaining())
        outBuffer.position(outBuffer.position() + written)
    }
}
