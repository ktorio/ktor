/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*

@Suppress("KDocMissingDocumentation")
internal data class TLSHandshakeMessage(
    val type: TLSHandshakeType = TLSHandshakeType.HelloRequest,
    val packet: ByteReadPacket = ByteReadPacket.Empty,
) {
    companion object : BytePacketSerializer<TLSHandshakeMessage> {
        override suspend fun read(input: ByteReadPacket): TLSHandshakeMessage {
            val typeAndLength = input.readInt()
            val length = typeAndLength and 0xffffff
            return TLSHandshakeMessage(
                type = TLSHandshakeType.byCode(typeAndLength ushr 24),
                packet = buildPacket {
                    writeFully(input.readBytes(length))
                }
            )
        }

        override fun write(output: BytePacketBuilder, value: TLSHandshakeMessage) {
            val typeAndLength = (value.type.code shl 24) or value.packet.remaining.toInt()
            output.writeInt(typeAndLength)
            output.writeFully(value.packet.readBytes())
        }
    }

    fun recordPacketCopy() = buildPacket {
        write(this@buildPacket, copy(packet = packet.copy()))
    }
}
