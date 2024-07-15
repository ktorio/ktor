@file:Suppress("RedundantModalityModifier")

package io.ktor.utils.io.core

import io.ktor.utils.io.*
import kotlinx.io.*

@Deprecated(
    IO_DEPRECATION_MESSAGE,
    replaceWith = ReplaceWith("Sink", "kotlinx.io.Buffer")
)
public typealias BytePacketBuilder = Sink

@Suppress("DEPRECATION")
@OptIn(InternalIoApi::class)
public val BytePacketBuilder.size: Int get() = buffer.size.toInt()

@Suppress("DEPRECATION")
public fun BytePacketBuilder(): BytePacketBuilder = kotlinx.io.Buffer()

@Suppress("DEPRECATION")
public fun BytePacketBuilder.append(value: CharSequence, startIndex: Int = 0, endIndex: Int = value.length) {
    writeText(value, startIndex, endIndex)
}

@OptIn(InternalIoApi::class)
@Suppress("DEPRECATION")
public fun BytePacketBuilder.build(): ByteReadPacket = buffer

@Suppress("DEPRECATION")
public fun BytePacketBuilder.writeFully(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size - offset) {
    write(buffer, offset, offset + length)
}

@Suppress("DEPRECATION")
public fun BytePacketBuilder.writePacket(packet: ByteReadPacket) {
    transferFrom(packet)
}

@Suppress("DEPRECATION")
public fun BytePacketBuilder.writeUByte(value: UByte) {
    writeByte(value.toByte())
}
