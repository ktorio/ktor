/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*

@Suppress("KDocMissingDocumentation")
internal class TLSRecord(
    val type: TLSRecordType = TLSRecordType.Handshake,
    val version: TLSVersion = TLSVersion.TLS12,
    val packet: ByteReadPacket = ByteReadPacket.Empty
)

@Suppress("KDocMissingDocumentation")
internal data class TLSHandshake(
    val type: TLSHandshakeType = TLSHandshakeType.HelloRequest,
    val packet: ByteReadPacket = ByteReadPacket.Empty,
) {
    companion object: BytePacketSerializer<TLSHandshake> {
        override suspend fun read(input: ByteReadPacket): TLSHandshake {
            val typeAndLength = input.readInt()
            val length = typeAndLength and 0xffffff
            return TLSHandshake(
                type = TLSHandshakeType.byCode(typeAndLength ushr 24),
                packet = buildPacket {
                    writeFully(input.readBytes(length))
                }
            )
        }

        override fun write(output: BytePacketBuilder, value: TLSHandshake) {
            val typeAndLength = (value.type.code shl 24) or value.packet.remaining.toInt()
            output.writeInt(typeAndLength)
            output.writeFully(value.packet.readBytes())
        }
    }
}
