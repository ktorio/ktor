package io.ktor.http.cio.websocket

import java.nio.*

@Suppress("NOTHING_TO_INLINE")
private inline infix fun Byte.xor(other: Byte) = toInt().xor(other.toInt()).toByte()

internal fun ByteBuffer.xor(other: ByteBuffer) {
    val bb = slice()
    val mask = other.slice()
    val maskSize = mask.remaining()

    for (i in 0 .. bb.remaining() - 1) {
        bb.put(i, bb.get(i) xor mask[i % maskSize])
    }
}

@Suppress("NOTHING_TO_INLINE")
internal inline fun Boolean.flagAt(at: Int) = if (this) 1 shl at else 0
