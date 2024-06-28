@file:Suppress("FunctionName")

package io.ktor.utils.io.core

import kotlinx.io.*
import kotlinx.io.Buffer
import kotlinx.io.unsafe.*
import java.nio.*

public fun ByteReadPacket(byteBuffer: ByteBuffer): Source = Buffer().also {
    it.write(byteBuffer)
}

@Suppress("DEPRECATION")
public fun ByteReadPacket.readAvailable(buffer: ByteBuffer): Int {
    val result = buffer.remaining()
    readAtMostTo(buffer)
    return result - buffer.remaining()
}

@Suppress("DEPRECATION")
public fun ByteReadPacket.readFully(buffer: ByteBuffer) {
    readAtMostTo(buffer)
}

@Suppress("DEPRECATION")
@OptIn(UnsafeIoApi::class, InternalIoApi::class)
public fun ByteReadPacket.read(block: (ByteBuffer) -> Unit) {
    UnsafeBufferOperations.readFromHead(buffer) { array, start, endExclusive ->
        val wrap = ByteBuffer.wrap(array, start, endExclusive - start)
        block(wrap)

        val consumed = wrap.position() - start
        consumed
    }
}
