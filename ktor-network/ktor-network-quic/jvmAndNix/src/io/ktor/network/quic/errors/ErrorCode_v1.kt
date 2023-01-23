/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.errors

import io.ktor.network.quic.bytes.*
import io.ktor.utils.io.core.*
import kotlin.jvm.*

/**
 * QUIC Error codes.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-error-codes)
 */
internal sealed interface ErrorCode_v1 {
    fun writeToFrame(packetBuilder: BytePacketBuilder)
}

/**
 * Application error codes that are produced and managed by application layer protocol
 *
 * Used in CONNECTION_CLOSED frames with the type of 0x1d
 */
@JvmInline
internal value class AppErrorCode_v1(val intCode: Long) : ErrorCode_v1 {
    override fun writeToFrame(packetBuilder: BytePacketBuilder) {
        packetBuilder.writeVarInt(intCode.toVarInt())
    }
}

/**
 * Transport layer error codes.
 *
 * Codes are divided into general errors [TransportErrorCode_v1] and [CryptoHandshakeError_v1]
 * to write them into frame efficiently (as first are always 1 byte length, and last are 2 bytes)
 *
 * Used in CONNECTION_CLOSED frames with the type of 0x1c
 */
internal sealed interface QUICTransportErrorCode_v1 : ErrorCode_v1

internal enum class TransportErrorCode_v1(val intCode: Byte) : QUICTransportErrorCode_v1 {
    NO_ERROR(0x00),
    INTERNAL_ERROR(0x01),
    CONNECTION_REFUSED(0x02),
    FLOW_CONTROL_ERROR(0x03),
    STREAM_LIMIT_ERROR(0x04),
    STREAM_STATE_ERROR(0x05),
    FINAL_SIZE_ERROR(0x06),
    FRAME_ENCODING_ERROR(0x07),
    TRANSPORT_PARAMETER_ERROR(0x08),
    CONNECTION_ID_LIMIT_ERROR(0x09),
    PROTOCOL_VIOLATION(0x0A),
    INVALID_TOKEN(0x0B),
    APPLICATION_ERROR(0x0C),
    CRYPTO_BUFFER_EXCEEDED(0x0D),
    KEY_UPDATE_ERROR(0x0E),
    AEAD_LIMIT_REACHED(0x0F),
    NO_VIABLE_PATH(0x10),
    ;

    override fun writeToFrame(packetBuilder: BytePacketBuilder) {
        // it is varint with length 8 (leading bits are 00)
        packetBuilder.writeByte(intCode)
    }
}

@JvmInline
internal value class CryptoHandshakeError_v1(val tlsAlertCode: Byte) : QUICTransportErrorCode_v1 {
    override fun writeToFrame(packetBuilder: BytePacketBuilder) {
        // 0b01000001, where 0100 prefix - varint length,
        //                   0001 byte - prefix of crypto error (reserved range: 0x0100..0x01ff)
        //
        // RFC Reference: https://www.rfc-editor.org/rfc/rfc9001#name-tls-errors
        packetBuilder.writeByte(0x41)
        packetBuilder.writeByte(tlsAlertCode)
    }
}
