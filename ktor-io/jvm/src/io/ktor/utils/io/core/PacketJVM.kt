package io.ktor.utils.io.core

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.utils.*
import java.nio.*
import java.nio.charset.CharsetDecoder

actual val PACKET_MAX_COPY_SIZE: Int = getIOIntProperty("max.copy.size", 500)

actual fun BytePacketBuilder(headerSizeHint: Int): BytePacketBuilder =
    BytePacketBuilder(headerSizeHint, ChunkBuffer.Pool)

actual typealias EOFException = java.io.EOFException

/**
 * Read exactly [n] (optional, read all remaining by default) bytes to a newly allocated byte buffer
 * @return a byte buffer containing [n] bytes
 */
fun ByteReadPacket.readByteBuffer(
    n: Int = remaining.coerceAtMostMaxIntOrFail("Unable to make a ByteBuffer: packet is too big"),
    direct: Boolean = false
): ByteBuffer {
    val bb: ByteBuffer = if (direct) ByteBuffer.allocateDirect(n) else ByteBuffer.allocate(n)
    readFully(bb)
    bb.clear()
    return bb
}

@Deprecated("Migrate parameters order", ReplaceWith("readText(out, decoder, max)"))
fun ByteReadPacket.readText(decoder: CharsetDecoder, out: Appendable, max: Int = Int.MAX_VALUE): Int {
    return decoder.decode(this, out, max)
}
