@file:Suppress("RedundantModalityModifier", "FunctionName")

package io.ktor.utils.io.core

import io.ktor.utils.io.pool.*
import kotlinx.io.*

@Deprecated(
    "Use Source instead",
    ReplaceWith("Source", "kotlinx.io.Buffer")
)
public typealias ByteReadPacket = Source

@Suppress("DEPRECATION")
public val ByteReadPacketEmpty: ByteReadPacket = kotlinx.io.Buffer()

@Suppress("DEPRECATION")
public fun ByteReadPacket(
    array: ByteArray,
    offset: Int = 0,
    length: Int = array.size
): ByteReadPacket = kotlinx.io.Buffer().apply {
    write(array, startIndex = offset, endIndex = offset + length)
}

@OptIn(InternalIoApi::class)
public val Source.remaining: Long
    get() = buffer.size

@Suppress("UNUSED_PARAMETER")
@Deprecated(
    "Use Buffer instead",
    ReplaceWith("Buffer()", "kotlinx.io.Buffer")
)
public fun Sink(pool: ObjectPool<*>): kotlinx.io.Buffer = kotlinx.io.Buffer()

@Deprecated(
    "Use Buffer instead",
    ReplaceWith("Buffer()", "kotlinx.io.Buffer")
)
public fun Sink(): kotlinx.io.Buffer = kotlinx.io.Buffer()

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun ByteReadPacket.readAvailable(out: Buffer): Int {
    val result = buffer.size
    out.transferFrom(this)
    return result.toInt()
}

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun ByteReadPacket.copy(): ByteReadPacket = buffer.copy()

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun ByteReadPacket.readShortLittleEndian(): Short {
    return buffer.readShortLe()
}

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun ByteReadPacket.discard(count: Long = Long.MAX_VALUE): Long {
    request(count)
    val countToDiscard = minOf(count, remaining)
    buffer.skip(countToDiscard)
    return countToDiscard
}

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public fun ByteReadPacket.takeWhile(block: (Buffer) -> Boolean) {
    while (!isEmpty && block(buffer)) {
    }
}

@Suppress("DEPRECATION")
public fun ByteReadPacket.readFully(out: ByteArray, offset: Int = 0, length: Int = out.size - offset) {
    readTo(out, offset, offset + length)
}

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class, ExperimentalStdlibApi::class)
public fun <T> ByteReadPacket.preview(function: (ByteReadPacket) -> T): T {
    return buffer.peek().use(function)
}

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class, ExperimentalStdlibApi::class)
public fun <T> BytePacketBuilder.preview(function: (ByteReadPacket) -> T): T {
    return buffer.peek().use(function)
}

@Suppress("DEPRECATION")
@Deprecated(
    "Use close instead",
    ReplaceWith("this.close()")
)
public fun ByteReadPacket.release() {
    close()
}
