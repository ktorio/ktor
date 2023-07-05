/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.utils.io.core.*

/**
 * Interface for all QUIC packets across all versions
 */
internal sealed interface QUICPacket {
    val destinationConnectionID: QUICConnectionID

    /**
     * Packet Number field if applicable, otherwise -1
     */
    val packetNumber: Long

    /**
     * Packet's payload if applicable, otherwise null
     */
    val payload: ByteReadPacket?

    fun toDebugString(withPayload: Boolean = false): String

    /**
     * Long header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-long-header)
     */
    sealed interface LongHeader : QUICPacket {
        val version: UInt32
        override val destinationConnectionID: QUICConnectionID
        val sourceConnectionID: QUICConnectionID

        override fun toDebugString(withPayload: Boolean): String {
            val payload = payload ?: ByteReadPacket.Empty

            return """
                $debugName Packet
                    Packet Number: $packetNumber
                    Version: $version
                    DCID: ${destinationConnectionID.value.toDebugString()}
                    SCID: ${sourceConnectionID.value.toDebugString()}
                    Payload: ${payload.toDebugString(withPayload)}
            """.trimIndent()
        }

        val debugName: String
    }

    /**
     * Short header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-short-header)
     */
    sealed interface ShortHeader : QUICPacket {
        override val destinationConnectionID: QUICConnectionID
    }
}

/**
 * Version Negotiation packet, version independent.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-version-negotiation)
 */
internal class QUICVersionNegotiationPacket(
    override val destinationConnectionID: QUICConnectionID,
    override val sourceConnectionID: QUICConnectionID,
    val supportedVersions: Array<Int>,
) : QUICPacket.LongHeader {
    override val version: UInt32 = QUICVersion.VersionNegotiation
    override val packetNumber: Long = -1
    override val payload: ByteReadPacket? = null

    override val debugName: String = "Version Negotiation"

    override fun toDebugString(withPayload: Boolean): String {
        return """
            $debugName Packet
                DCID: ${destinationConnectionID.value.toDebugString()}
                SCID: ${sourceConnectionID.value.toDebugString()}
                Supported versions: ${supportedVersions.joinToString()}
        """.trimIndent()
    }
}

/**
 * Initial packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-initial-packet)
 */
internal class QUICInitialPacket(
    override val version: UInt32,
    override val destinationConnectionID: QUICConnectionID,
    override val sourceConnectionID: QUICConnectionID,
    val token: ByteArray,
    override val packetNumber: Long,
    override val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader {
    override val debugName: String = "Initial"

    override fun toDebugString(withPayload: Boolean): String {
        return """
            $debugName Packet
                Packet Number: $packetNumber
                Version: $version
                DCID: ${destinationConnectionID.value.toDebugString()}
                SCID: ${sourceConnectionID.value.toDebugString()}
                Token: ${token.toDebugString()}
                Payload: ${payload.toDebugString(withPayload)}
        """.trimIndent()
    }
}

/**
 * 0-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-0-rtt)
 */
internal class QUICZeroRTTPacket(
    override val version: UInt32,
    override val destinationConnectionID: QUICConnectionID,
    override val sourceConnectionID: QUICConnectionID,
    override val packetNumber: Long,
    override val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader {
    override val debugName: String = "0-RTT"
}

/**
 * Handshake packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-handshake-packet)
 */
internal class QUICHandshakePacket(
    override val version: UInt32,
    override val destinationConnectionID: QUICConnectionID,
    override val sourceConnectionID: QUICConnectionID,
    override val packetNumber: Long,
    override val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader {
    override val debugName: String = "Handshake"
}

/**
 * Retry packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-retry-packet)
 */
internal class QUICRetryPacket(
    override val version: UInt32,
    override val destinationConnectionID: QUICConnectionID,
    override val sourceConnectionID: QUICConnectionID,
    val retryToken: ByteArray,
    val retryIntegrityTag: ByteArray,
) : QUICPacket.LongHeader {
    override val packetNumber: Long = -1
    override val payload: ByteReadPacket? = null

    override val debugName: String = "Retry"

    override fun toDebugString(withPayload: Boolean): String {
        return """
            $debugName Packet
                Version: $version
                DCID: ${destinationConnectionID.value.toDebugString()}
                SCID: ${sourceConnectionID.value.toDebugString()}
                Retry Token: ${retryToken.toDebugString()}
                Retry Integrity Tag: ${retryIntegrityTag.toDebugString()} 
        """.trimIndent()
    }
}

/**
 * 1-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-1-rtt-packet)
 */
internal class QUICOneRTTPacket(
    override val destinationConnectionID: QUICConnectionID,
    val spinBit: Boolean,
    val keyPhase: Boolean,
    override val packetNumber: Long,
    override val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.ShortHeader {
    override fun toDebugString(withPayload: Boolean): String {
        return """
            1-RTT Packet
                Packet Number: $packetNumber
                Spin bit: $spinBit
                Key Phase: $keyPhase
                DCID: ${destinationConnectionID.value.toDebugString()}
                Payload: ${payload.toDebugString(withPayload)}
        """.trimIndent()
    }
}
