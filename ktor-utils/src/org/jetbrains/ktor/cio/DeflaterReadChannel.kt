package org.jetbrains.ktor.cio

import org.jetbrains.ktor.util.*
import java.nio.*
import java.util.zip.*

private class DeflaterReadChannel(val source: ReadChannel, val gzip: Boolean = true, val bufferPool: ByteBufferPool = NoPool) : ReadChannel {
    private val crc = CRC32()
    private val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    private val input = bufferPool.allocate(8192)

    private val compressed = bufferPool.allocate(8192).apply {
        if (gzip) {
            putGzipHeader(buffer)
        }

        buffer.flip()
    }
    private val trailing = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).apply {
        flip()
    }

    override fun close() {
        bufferPool.release(input)
        bufferPool.release(compressed)
        source.close()
    }

    override suspend fun read(dst: ByteBuffer): Int {
        if (!dst.hasRemaining())
            return 0

        if (deflater.finished()) {
            return readFinish(dst)
        }

        fillCompressedBufferIfPossible()

        if (compressed.buffer.hasRemaining()) {
            return compressed.buffer.putTo(dst)
        }

        while (true) {
            input.buffer.clear()
            val inputSize = source.read(input.buffer)
            if (inputSize == -1) {
                return readFinish(dst)
            }

            input.buffer.flip()
            crc.updateKeepPosition(input.buffer)
            deflater.setInput(input.buffer)

            var counter = 0
            while (!deflater.needsInput() && dst.hasRemaining()) {
                compressed.buffer.compact()

                deflater.deflate(compressed.buffer)
                compressed.buffer.flip()

                counter += compressed.buffer.putTo(dst)
            }

            if (counter > 0 || !dst.hasRemaining())
                return counter
        }
    }

    private fun readFinish(dst: ByteBuffer): Int {
        if (!deflater.finished()) {
            finish()
        }

        val size = compressed.buffer.putTo(dst) + if (compressed.buffer.hasRemaining()) 0 else trailing.putTo(dst)
        if (size == 0) {
            return -1
        } else {
            return size
        }
    }

    private fun fillCompressedBufferIfPossible() {
        if (!deflater.needsInput()) {
            compressed.buffer.compact()

            if (!deflater.needsInput() && compressed.buffer.hasRemaining()) {
                deflater.deflate(compressed.buffer)
            }

            compressed.buffer.flip()
        }
    }

    private fun finish() {
        deflater.finish()
        compressed.buffer.compact()

        while (!deflater.finished() && compressed.buffer.hasRemaining()) {
            deflater.deflate(compressed.buffer)
        }

        if (deflater.finished()) {
            prepareTrailer()
            trailing.putTo(compressed.buffer)
        }

        compressed.buffer.flip()
    }

    private fun prepareTrailer() {
        if (gzip) {
            trailing.clear()
            putGzipTrailer(crc, deflater, trailing)
            trailing.flip()
        }
    }

}

fun ReadChannel.deflated(gzip: Boolean = true): ReadChannel = DeflaterReadChannel(this, gzip)
