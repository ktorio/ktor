package io.ktor.cio

import kotlinx.coroutines.experimental.*
import io.ktor.util.*
import java.nio.*
import java.util.concurrent.atomic.*
import java.util.zip.*

private class DeflaterWriteChannel(val gzip: Boolean, val out: WriteChannel, val bufferPool: ByteBufferPool = NoPool) : WriteChannel {
    private val crc = CRC32()
    private val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
    private var header = false
    private var trailing = false
    private val t = bufferPool.allocate(8192)
    private val buffer = t.buffer
    private val released = AtomicBoolean(false)

    suspend override fun write(src: ByteBuffer) {
        if (released.get()) {
            throw IllegalStateException("Already closed")
        }

        buffer.clear()
        ensureGzipHeader()

        if (src.hasRemaining()) {
            crc.updateKeepPosition(src)
            deflater.setInput(src)

            deflateWhile { !deflater.needsInput() }

            deflater.setInput(EMPTY) // causes monitor lock-unlock iteration but we need this to eliminate internal reference to buffer's array
            src.position(src.limit())
        }

        buffer.flip()
        writeOutgoingBytes()
    }

    suspend override fun flush() {
        if (released.get()) {
            throw IllegalStateException("Already closed")
        }

        buffer.clear()
        ensureGzipHeader()
        buffer.flip()

        writeOutgoingBytes()
        out.flush()
    }

    override fun close() {
        if (released.compareAndSet(false, true)) {
            runBlocking {
                buffer.clear()
                ensureGzipHeader()

                deflater.finish()
                deflateWhile { !deflater.finished() }

                if (gzip && !trailing) {
                    trailing = true

                    while (buffer.remaining() < GZIP_TRAILER_SIZE) {
                        buffer.flip()
                        out.write(buffer)
                        buffer.compact()
                    }

                    putGzipTrailer(crc, deflater, buffer)
                }

                buffer.flip()
                writeOutgoingBytes()
            }

            bufferPool.release(t)
            deflater.end()
        }
    }

    private fun ensureGzipHeader() {
        if (gzip && !header) {
            header = true
            putGzipHeader(buffer)
        }
    }

    private suspend fun deflateWhile(predicate: () -> Boolean) {
        while (predicate()) {
            deflater.deflate(buffer)

            if (predicate()) { // don't trigger write on tail compressed part so we can combine it with consequent bytes
                buffer.flip()

                writeOutgoingBytes()
                buffer.compact()
            }
        }
    }

    private suspend fun writeOutgoingBytes() {
        while (buffer.hasRemaining()) {
            out.write(buffer)
        }
    }

    companion object {
        private val EMPTY = ByteArray(0)
    }
}

fun WriteChannel.deflated(gzip: Boolean = true): WriteChannel = DeflaterWriteChannel(gzip, this)