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
    val destinationConnectionID: ConnectionID

    /**
     * Long header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-long-header)
     */
    sealed interface LongHeader : QUICPacket {
        val version: UInt32
        override val destinationConnectionID: ConnectionID
        val sourceConnectionID: ConnectionID
    }

    /**
     * Short header for QUIC packets with version independent properties.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-short-header)
     */
    sealed interface ShortHeader : QUICPacket {
        override val destinationConnectionID: ConnectionID
    }
}

/**
 * Version Negotiation packet, version independent.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc8999.html#name-version-negotiation)
 */
internal class VersionNegotiationPacket(
    override val destinationConnectionID: ConnectionID,
    override val sourceConnectionID: ConnectionID,
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
    override val destinationConnectionID: ConnectionID,
    override val sourceConnectionID: ConnectionID,
    val token: ByteArray,
    val packetNumber: Long,
    val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader

/**
 * 0-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-0-rtt)
 */
internal class ZeroRTTPacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ConnectionID,
    override val sourceConnectionID: ConnectionID,
    val packetNumber: Long,
    val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader

/**
 * Handshake packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-handshake-packet)
 */
internal class HandshakePacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ConnectionID,
    override val sourceConnectionID: ConnectionID,
    val packetNumber: Long,
    val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.LongHeader

/**
 * Retry packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-retry-packet)
 */
internal class RetryPacket_v1(
    override val version: UInt32,
    override val destinationConnectionID: ConnectionID,
    override val sourceConnectionID: ConnectionID,
    val retryToken: ByteArray,
    val retryIntegrityTag: ByteArray,
) : QUICPacket.LongHeader

/**
 * 1-RTT packet as it defined in QUIC version 1.
 *
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-1-rtt-packet)
 */
internal class OneRTTPacket_v1(
    override val destinationConnectionID: ConnectionID,
    val spinBit: Boolean,
    val keyPhase: Boolean,
    val packetNumber: Long,
    val payload: ByteReadPacket = ByteReadPacket.Empty,
) : QUICPacket.ShortHeader

/**
 * Returns Packet Number field if the packet contains it, otherwise -1
 */
internal val QUICPacket.packetNumber: Long get() = when (this) {
    is OneRTTPacket_v1 -> this.packetNumber
    is ZeroRTTPacket_v1 -> this.packetNumber
    is InitialPacket_v1 -> this.packetNumber
    is HandshakePacket_v1 -> this.packetNumber
    else -> -1
}
