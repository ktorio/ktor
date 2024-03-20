/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*

/**
 * TLS version
 * @property code numeric TLS version code
 */
@Suppress("KDocMissingDocumentation")
public enum class TLSVersion(public val code: Int) {
    SSL3(0x0300),
    TLS10(0x0301),
    TLS11(0x0302),
    TLS12(0x0303),
    TLS13(0x0304);

    public companion object : ByteChannelSerializer<TLSVersion>, BytePacketSerializer<TLSVersion> {
        private val byOrdinal = values()

        /**
         * Find version instance by its numeric [code] or fail
         */
        public fun byCode(code: Int): TLSVersion = when (code) {
            in 0x0300..0x0303 -> byOrdinal[code - 0x0300]
            else -> throw IllegalArgumentException("Invalid TLS version code $code")
        }

        override suspend fun read(input: ByteReadChannel): TLSVersion =
            TLSVersion.byCode(input.readShortCompatible() and 0xffff)

        override suspend fun read(input: ByteReadPacket): TLSVersion =
            byCode(input.readShort().toInt() and 0xffff)

        override suspend fun write(output: ByteWriteChannel, value: TLSVersion) {
            output.writeByte((value.code shr 8).toByte())
            output.writeByte(value.code.toByte())
        }

        override fun write(output: BytePacketBuilder, value: TLSVersion) {
            output.writeByte((value.code shr 8).toByte())
            output.writeByte(value.code.toByte())
        }
    }
}
