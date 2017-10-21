package io.ktor.util

import java.nio.*
import java.util.zip.*

internal val GZIP_MAGIC = 0x8b1f
internal val GZIP_TRAILER_SIZE = 8

internal fun Deflater.deflate(outBuffer: ByteBuffer) {
    if (outBuffer.hasRemaining()) {
        val written = deflate(outBuffer.array(), outBuffer.arrayOffset() + outBuffer.position(), outBuffer.remaining())
        outBuffer.position(outBuffer.position() + written)
    }
}

internal fun Deflater.setInput(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    setInput(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

internal fun Checksum.updateKeepPosition(buffer: ByteBuffer) {
    require(buffer.hasArray()) { "buffer need to be array-backed" }
    update(buffer.array(), buffer.arrayOffset() + buffer.position(), buffer.remaining())
}

internal fun putGzipHeader(buffer: ByteBuffer) {
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.putShort(GZIP_MAGIC.toShort())
    buffer.put(Deflater.DEFLATED.toByte())
    buffer.position(buffer.position() + 7)
}

internal fun putGzipTrailer(crc: Checksum, deflater: Deflater, trailing: ByteBuffer) {
    trailing.putInt(crc.value.toInt())
    trailing.putInt(deflater.totalIn)
}
