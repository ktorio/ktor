/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.utils.io.core.*

/**
 * Interface for all QUIC packets across all versions
 */
internal sealed interface QUICPacket {
    /**
     * Long header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-long-header)
     */
    sealed interface LongHeader : QUICPacket {
        val version: UInt32
        val destinationConnectionID: ByteArray
        val sourceConnectionID: ByteArray
    }

    /**
     * Short header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-short-header)
     */
    sealed interface ShortHeader : QUICPacket {
        val destinationConnectionId: ByteArray
    }
}

/**
 * Version Negotiation packet, version independent.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-version-negotiation)
 */
internal class VersionNegotiationPacket(
    override val destinationConnectionID: ByteArray,
    override val sourceConnectionID: ByteArray,
    val supportedVersions: Array<Int>,
) : QUICPacket.LongHeader {
    override val version: UInt32 = QUICVersion.VersionNegotiation
}

/**
 * Initial packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-initial-packet)
 */
internal class InitialPacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ByteArray,
    override val sourceConnectionID: ByteArray,
    val reservedBits: Int,
    val token: ByteArray,
    val packetNumber: Long,
    val payload: ByteReadPacket,
) : QUICPacket.LongHeader

/**
 * 0-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-0-rtt)
 */
internal class ZeroRTTPacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ByteArray,
    override val sourceConnectionID: ByteArray,
    val reservedBits: Int,
    val packetNumber: Long,
    val payload: ByteReadPacket,
) : QUICPacket.LongHeader

/**
 * Handshake packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-handshake-packet)
 */
internal class HandshakePacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ByteArray,
    override val sourceConnectionID: ByteArray,
    val reservedBits: Int,
    val packetNumber: Long,
    val payload: ByteReadPacket,
) : QUICPacket.LongHeader

/**
 * Retry packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-retry-packet)
 */
internal class RetryPacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ByteArray,
    override val sourceConnectionID: ByteArray,
    val retryToken: ByteArray,
    val retryIntegrityTag: ByteArray,
) : QUICPacket.LongHeader

/**
 * 1-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-1-rtt-packet)
 */
internal class OneRTTPacket_v1(
    override val destinationConnectionId: ByteArray,
    val spinBit: Boolean,
    val reservedBits: Int,
    val keyPhase: Boolean,
    val packetNumber: Long,
    val payload: ByteReadPacket,
) : QUICPacket.ShortHeader
