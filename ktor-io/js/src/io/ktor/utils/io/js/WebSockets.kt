package io.ktor.utils.io.js

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*
import org.w3c.dom.*

fun WebSocket.sendPacket(packet: ByteReadPacket) {
    send(packet.readArrayBuffer())
}

inline fun WebSocket.sendPacket(block: BytePacketBuilder.() -> Unit) {
    sendPacket(buildPacket(block = block))
}

inline fun MessageEvent.packet(): ByteReadPacket {
    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE", "UnsafeCastFromDynamic", "DEPRECATION")
    return ByteReadPacket(IoBuffer(Memory.of(data.asDynamic() as DataView), null), ChunkBuffer.NoPool)
}




