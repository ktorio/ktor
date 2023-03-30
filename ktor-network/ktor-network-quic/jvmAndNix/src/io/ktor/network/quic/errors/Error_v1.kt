/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.errors

import io.ktor.network.quic.bytes.*
import io.ktor.utils.io.core.*
import kotlin.jvm.JvmInline

/**
 * 0b01000001, where 0100 prefix - varint length, 0001 byte - prefix of crypto error.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9001#name-tls-errors)
 */
private const val CRYPTO_HANDSHAKE_ERROR_PREFIX = 0x41

/**
 * Application error codes that are produced and managed by application layer protocol
 *
 * Used in CONNECTION_CLOSED frames with the type of 0x1d
 */
@JvmInline
internal value class AppError(val intCode: Long) {
    fun writeToFrame(packetBuilder: BytePacketBuilder) {
        packetBuilder.writeVarInt(intCode)
    }
}

/**
 * Transport layer error codes.
 *
 * Codes are divided into general errors [TransportError_v1] and [CryptoHandshakeError_v1]
 * to write them into frame efficiently (as first are always 1 byte length, and the last are 2 bytes)
 *
 * Used in CONNECTION_CLOSED frames with the type of 0x1c
 *
 * This is QUIC version 1 errors, future versions of protocol may contain other errors and corresponding codes
 */
internal sealed interface QUICTransportError_v1 : QUICTransportError {
    fun writeToFrame(packetBuilder: BytePacketBuilder)

    companion object {
        fun readFromFrame(payload: ByteReadPacket): QUICTransportError_v1? {
            val byte = payload.readUInt8 { return null }.toInt()
            val length = byte ushr 6
            return when {
                length == 0 -> TransportError_v1.fromErrorCode(byte)

                length == 1 && byte == CRYPTO_HANDSHAKE_ERROR_PREFIX -> {
                    CryptoHandshakeError_v1(payload.readUInt8 { return null })
                }

                else -> null
            }
        }
    }
}

internal enum class TransportError_v1(val intCode: UInt8) : QUICTransportError_v1 {
    NO_ERROR(0x00u),
    INTERNAL_ERROR(0x01u),
    CONNECTION_REFUSED(0x02u),
    FLOW_CONTROL_ERROR(0x03u),
    STREAM_LIMIT_ERROR(0x04u),
    STREAM_STATE_ERROR(0x05u),
    FINAL_SIZE_ERROR(0x06u),
    FRAME_ENCODING_ERROR(0x07u),
    TRANSPORT_PARAMETER_ERROR(0x08u),
    CONNECTION_ID_LIMIT_ERROR(0x09u),
    PROTOCOL_VIOLATION(0x0Au),
    INVALID_TOKEN(0x0Bu),
    APPLICATION_ERROR(0x0Cu),
    CRYPTO_BUFFER_EXCEEDED(0x0Du),
    KEY_UPDATE_ERROR(0x0Eu),
    AEAD_LIMIT_REACHED(0x0Fu),
    NO_VIABLE_PATH(0x10u),
    ;

    override fun writeToFrame(packetBuilder: BytePacketBuilder) {
        // it is varint with length 8 (leading bits are 00)
        packetBuilder.writeUInt8(intCode)
    }

    override fun toDebugString(): String {
        return "Transport Error $name, code: $intCode"
    }

    companion object {
        private val array = TransportError_v1.values()

        fun fromErrorCode(code: Int): TransportError_v1? {
            return when {
                code > 0x10 -> null
                else -> array[code]
            }
        }
    }
}

internal class CryptoHandshakeError_v1(val tlsAlertCode: UInt8) : QUICTransportError_v1 {
    override fun writeToFrame(packetBuilder: BytePacketBuilder) {
        packetBuilder.writeUInt8(CRYPTO_HANDSHAKE_ERROR_PREFIX.toUByte())
        packetBuilder.writeUInt8(tlsAlertCode)
    }

    override fun toDebugString(): String {
        return "Crypto Handshake Error: $tlsAlertCode"
    }
}

internal class ReasonedError(
    val error: QUICTransportError,
    val reasonPhrase: ByteArray,
) : QUICTransportError {
    override fun toDebugString(): String {
        return "Error: ${error.toDebugString()}, reason: ${String(reasonPhrase)}"
    }
}

internal operator fun QUICTransportError.invoke(reasonPhrase: String): QUICTransportError {
    if (reasonPhrase.isEmpty()) return this

    val bytes = reasonPhrase.toByteArray()
    return when (this) {
        is ReasonedError -> ReasonedError(error, bytes)
        else -> ReasonedError(this, bytes)
    }
}
