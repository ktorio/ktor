/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.js

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import org.khronos.webgl.*
import org.w3c.dom.*

public fun WebSocket.sendPacket(packet: ByteReadPacket) {
    send(packet.readArrayBuffer())
}

public inline fun WebSocket.sendPacket(block: BytePacketBuilder.() -> Unit) {
    sendPacket(buildPacket(block = block))
}

@Suppress("DEPRECATION")
public inline fun MessageEvent.packet(): ByteReadPacket {
    val data = Int8Array((data as DataView).buffer).toByteArray()
    @Suppress("NON_PUBLIC_CALL_FROM_PUBLIC_INLINE", "UnsafeCastFromDynamic")
    return ByteReadPacket(
        ChunkBuffer(Memory(data), null, ChunkBuffer.NoPool),
        ChunkBuffer.NoPool
    )
}
