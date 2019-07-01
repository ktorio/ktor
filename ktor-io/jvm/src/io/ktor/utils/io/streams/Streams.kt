package io.ktor.utils.io.streams

import io.ktor.utils.io.core.*
import java.io.*
import java.io.EOFException

/**
 * Write the whole packet to the stream once it is built via [builder] lambda
 */
fun OutputStream.writePacket(builder: BytePacketBuilder.() -> Unit) {
    writePacket(buildPacket(block = builder))
}

/**
 * Write the whole [packet] to the stream
 */
fun OutputStream.writePacket(packet: ByteReadPacket) {
    val s = packet.remaining
    if (s == 0L) return
    val buffer = ByteArray(s.coerceAtMost(4096L).toInt())

    try {
        while (packet.isNotEmpty) {
            val size = packet.readAvailable(buffer)
            write(buffer, 0, size)
        }
    } finally {
        packet.release()
    }
}

/**
 * Read a packet of exactly [n] bytes
 */
fun InputStream.readPacketExact(n: Long): ByteReadPacket = readPacketImpl(n, n)

/**
 * Read a packet of at least [n] bytes or all remaining. Does fail if not enough bytes remaining.
 */
fun InputStream.readPacketAtLeast(n: Long): ByteReadPacket = readPacketImpl(n, Long.MAX_VALUE)

/**
 * Read a packet of at most [n] bytes. Resulting packet could be empty however this function does always reads
 * as much bytes as possible.
 */
fun InputStream.readPacketAtMost(n: Long): ByteReadPacket = readPacketImpl(1L, n)

private fun InputStream.readPacketImpl(min: Long, max: Long): ByteReadPacket {
    require(min >= 0L) { "min shouldn't be negative" }
    require(min <= max) { "min shouldn't be greater than max: $min > $max" }

    val buffer = ByteArray(max.coerceAtMost(4096).toInt())
    val builder = BytePacketBuilder()

    var read = 0L

    try {
        while (read < min || (read == min && min == 0L)) {
            val remInt = minOf(max - read, Int.MAX_VALUE.toLong()).toInt()
            val rc = read(buffer, 0, minOf(remInt, buffer.size))
            if (rc == -1) throw EOFException("Premature end of stream: was read $read bytes of $min")
            read += rc
            builder.writeFully(buffer, 0, rc)
        }
    } catch (t: Throwable) {
        builder.release()
        throw t
    }

    return builder.build()
}

private val SkipBuffer = CharArray(8192)

/**
 * Creates [InputStream] adapter to the packet
 */
fun ByteReadPacket.inputStream(): InputStream {
    return object : InputStream() {
        override fun read(): Int {
            if (isEmpty) return -1
            return readByte().toInt() and 0xff
        }

        override fun available() = remaining.coerceAtMostMaxInt()

        override fun close() {
            release()
        }
    }
}

/**
 * Creates [Reader] from the byte packet that decodes UTF-8 characters
 */
fun ByteReadPacket.readerUTF8(): Reader {
    return object : Reader() {
        override fun close() {
            release()
        }

        override fun skip(n: Long): Long {
            var skipped = 0L
            val buffer = SkipBuffer
            val bufferSize = buffer.size

            while (skipped < n) {
                val size = minOf(bufferSize.toLong(), n - skipped).toInt()
                val rc = read(buffer, 0, size)
                if (rc == -1) break
                skipped += rc
            }

            return skipped
        }

        override fun read(cbuf: CharArray, off: Int, len: Int) = readAvailableCharacters(cbuf, off, len)
    }
}


/**
 * Creates [OutputStream] adapter to the builder
 */
fun BytePacketBuilder.outputStream(): OutputStream {
    return object : OutputStream() {
        override fun write(b: Int) {
            writeByte(b.toByte())
        }

        override fun write(b: ByteArray, off: Int, len: Int) {
            this@outputStream.writeFully(b, off, len)
        }

        override fun close() {
        }
    }
}

/**
 * Creates [Writer] that encodes all characters in UTF-8 encoding
 */
fun BytePacketBuilder.writerUTF8(): Writer {
    return object : Writer() {
        override fun write(cbuf: CharArray, off: Int, len: Int) {
            append(cbuf, off, off + len)
        }

        override fun flush() {
        }

        override fun close() {
        }
    }
}
