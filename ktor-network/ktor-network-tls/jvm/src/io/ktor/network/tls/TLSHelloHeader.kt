/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.tls

import io.ktor.utils.io.core.*

internal data class TLSHelloHeader(
    val version: TLSVersion,
    val seed: ByteArray,
    val sessionId: ByteArray
) {
    companion object : BytePacketSerializer<TLSHelloHeader> {

        override suspend fun read(input: ByteReadPacket): TLSHelloHeader {
            val version = TLSVersion.read(input)

            val random = ByteArray(32)
            input.readFully(random)
            val sessionIdLength = input.readByte().toInt() and 0xff

            if (sessionIdLength > 32) {
                throw TLSValidationException("sessionId length limit of 32 bytes exceeded: $sessionIdLength specified")
            }

            val sessionId = ByteArray(32)
            input.readFully(sessionId, 0, sessionIdLength)

            return TLSHelloHeader(version, random, sessionId)
        }

        override fun write(output: BytePacketBuilder, value: TLSHelloHeader) = with(output) {
            // version
            writeShort(value.version.code.toShort())

            // random
            writeFully(value.seed)

            // sessionId
            val sessionIdLength = value.sessionId.size
            if (sessionIdLength > 0xff) {
                throw TLSValidationException("Illegal sessionIdLength")
            }

            writeByte(sessionIdLength.toByte())
            writeFully(value.sessionId, 0, sessionIdLength)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TLSHelloHeader) return false

        if (version != other.version) return false
        if (!seed.contentEquals(other.seed)) return false
        if (!sessionId.contentEquals(other.sessionId)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + seed.contentHashCode()
        result = 31 * result + sessionId.contentHashCode()
        return result
    }
}
