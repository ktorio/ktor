/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

@Suppress("KDocMissingDocumentation")
internal class TLSRecord(
    val type: TLSRecordType,
    val version: TLSVersion,
    val packet: ByteReadPacket = ByteReadPacket.Empty
) {
    companion object : ByteChannelSerializer<TLSRecord> {
        private const val MAX_TLS_FRAME_SIZE = 0x4800

        override suspend fun write(output: ByteWriteChannel, value: TLSRecord) = with(output) {
            writeByte(value.type.code.toByte())
            TLSVersion.write(output, value.version)
            writeShort(value.packet.remaining.toShort())
            writePacket(value.packet)
            flush()
        }
        override suspend fun read(input: ByteReadChannel): TLSRecord = with(input) {
            val type = TLSRecordType.byCode(readByte().toInt() and 0xff)
            val version = TLSVersion.read(input)

            val length = readShortCompatible() and 0xffff
            if (length > MAX_TLS_FRAME_SIZE) throw TLSValidationException("Illegal TLS frame size: $length")

            return TLSRecord(type, version, readPacket(length))
        }
    }
}
