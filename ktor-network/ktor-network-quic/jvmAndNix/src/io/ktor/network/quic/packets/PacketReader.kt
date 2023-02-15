/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.crypto.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.errors.TransportError_v1.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlin.properties.*

internal object PacketReader {
    private const val HEADER_TYPE: UInt8 = 0x80u
    private const val FIXED_BIT: UInt8 = 0x40u

    private const val LONG_HEADER_PACKET_TYPE: UInt8 = 0x30u
    private const val LONG_HEADER_PACKET_TYPE_INITIAL: UInt8 = 0x00u
    private const val LONG_HEADER_PACKET_TYPE_0_RTT: UInt8 = 0x10u
    private const val LONG_HEADER_PACKET_TYPE_HANDSHAKE: UInt8 = 0x20u
    private const val LONG_HEADER_PACKET_TYPE_RETRY: UInt8 = 0x30u

    private const val LONG_HEADER_RESERVED_BITS: UInt8 = 0x0Cu
    private const val LONG_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u

    private const val SHORT_HEADER_SPIN_BIT: UInt8 = 0x20u
    private const val SHORT_HEADER_RESERVED_BITS: UInt8 = 0x18u
    private const val SHORT_HEADER_KEY_PHASE: UInt8 = 0x04u
    private const val SHORT_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u

    private const val RETRY_PACKET_INTEGRITY_TAG_LENGTH = 128

    /**
     * Reads a single QUIC packet.
     *
     * @param negotiatedVersion - used for the short headers.
     * Version of the protocol that is used for this connection.
     */
    inline fun readSinglePacket(
        bytes: ByteReadPacket,
        negotiatedVersion: UInt32,
        onError: (QUICTransportError) -> Nothing,
    ): QUICPacket {
        val flags = bytes.readUInt8 { onError(PROTOCOL_VIOLATION) }

        if (flags and HEADER_TYPE == HEADER_TYPE) { // Long Header bit is set
            // Version independent properties of packets with the Long Header

            val version = bytes.readUInt32 { onError(PROTOCOL_VIOLATION) }

            // Connection ID max size may vary between versions
            val maxCIDLength = MaxCIDLength.fromVersion(version) { onError(FRAME_ENCODING_ERROR) }

            val destinationConnectionID = readConnectionID(bytes, maxCIDLength, onError)
            val sourceConnectionID: ByteArray = readConnectionID(bytes, maxCIDLength, onError)

            // End of version independent properties

            if (version == QUICVersion.VersionNegotiation) {
                return readVersionNegotiationPacket(bytes, destinationConnectionID, sourceConnectionID, onError)
            }

            return readLongHeader_v1(bytes, flags, version, destinationConnectionID, sourceConnectionID, onError)
        } else { // Short Header bit is set
            val maxCIDLength = MaxCIDLength.fromVersion(negotiatedVersion) { onError(FRAME_ENCODING_ERROR) }

            // Version independent properties of packets with the Short Header

            val destinationConnectionID = readConnectionID(bytes, maxCIDLength, onError)

            // End of version independent properties

            return readShortHeader_v1(bytes, flags, destinationConnectionID, onError)
        }
    }

    private inline fun readConnectionID(
        bytes: ByteReadPacket,
        maxCIDLength: UInt8,
        onError: (QUICTransportError) -> Nothing,
    ): ByteArray {
        val connectionIDLength = bytes.readUInt8 { onError(PROTOCOL_VIOLATION) }
        if (connectionIDLength > maxCIDLength) {
            onError(PROTOCOL_VIOLATION)
        }
        return bytes.readBytes(connectionIDLength.toInt())
    }

    private inline fun readVersionNegotiationPacket(
        bytes: ByteReadPacket,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        onError: (QUICTransportError) -> Nothing,
    ): VersionNegotiationPacket {
        // supportedVersions is an array of 32-bit integers with no specified length
        if (bytes.remaining % 4 != 0L) {
            onError(PROTOCOL_VIOLATION)
        }

        val supportedVersions = Array((bytes.remaining / 4).toInt()) { bytes.readInt() }

        return VersionNegotiationPacket(destinationConnectionID, sourceConnectionID, supportedVersions)
    }

    /**
     * Reads a packet with a Long Header as it specified in QUIC version 1
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets)
     */
    @Suppress("SameParameterValue")
    private inline fun readLongHeader_v1(
        bytes: ByteReadPacket,
        flags: UInt8,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        onError: (QUICTransportError) -> Nothing,
    ): QUICPacket.LongHeader {
        // The next bit (0x40) of byte 0 is set to 1, unless the packet is a Version Negotiation packet.
        // Packets containing a zero value for this bit are not valid packets in this version and MUST be discarded.
        if (flags and FIXED_BIT != FIXED_BIT) {
            onError(PROTOCOL_VIOLATION)
        }

        val type = when (flags and LONG_HEADER_PACKET_TYPE) {
            LONG_HEADER_PACKET_TYPE_INITIAL -> PacketType_v1.Initial
            LONG_HEADER_PACKET_TYPE_0_RTT -> PacketType_v1.ZeroRTT
            LONG_HEADER_PACKET_TYPE_HANDSHAKE -> PacketType_v1.Handshake
            LONG_HEADER_PACKET_TYPE_RETRY -> PacketType_v1.Retry
            else -> unreachable()
        }

        return when (type) {
            PacketType_v1.Initial, PacketType_v1.ZeroRTT, PacketType_v1.Handshake -> {
                val reservedBits = (flags and LONG_HEADER_RESERVED_BITS).toInt() ushr 2

                var token: ByteArray? = null
                if (type == PacketType_v1.Initial) {
                    val tokenLength = bytes.readVarIntOrElse { onError(PROTOCOL_VIOLATION) }
                    token = bytes.readBytes(tokenLength.toInt())
                }

                val length = bytes.readVarIntOrElse { onError(PROTOCOL_VIOLATION) }

                val packetNumber = readAndDecodePacketNumber(
                    bytes = bytes,
                    packetNumberLengthEncrypted = flags and LONG_HEADER_PACKET_NUMBER_LENGTH,
                    largestPacketNumberInSpace = -1, // todo
                    onError = onError
                )

                when (type) {
                    PacketType_v1.Initial -> InitialPacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        token = token!!,
                        packetNumber = packetNumber,
                        length = length,
                        payload = bytes,
                    )

                    PacketType_v1.ZeroRTT -> ZeroRTTPacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        packetNumber = packetNumber,
                        length = length,
                        payload = bytes,
                    )

                    PacketType_v1.Handshake -> HandshakePacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        packetNumber = packetNumber,
                        length = length,
                        payload = bytes,
                    )

                    else -> unreachable()
                }
            }

            PacketType_v1.Retry -> {
                val retryTokenLength = bytes.remaining - RETRY_PACKET_INTEGRITY_TAG_LENGTH
                if (retryTokenLength < 0) {
                    onError(PROTOCOL_VIOLATION)
                }
                val retryToken = bytes.readBytes(retryTokenLength.toInt())
                val integrityTag = bytes.readBytes(RETRY_PACKET_INTEGRITY_TAG_LENGTH)

                // todo decode first?
                RetryPacket_v1(version, destinationConnectionID, sourceConnectionID, retryToken, integrityTag)
            }
        }
    }

    /**
     * Reads a packet with a Short Header as it specified in QUIC version 1
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-short-header-packets)
     */
    private inline fun readShortHeader_v1(
        bytes: ByteReadPacket,
        flags: UInt8,
        destinationConnectionID: ByteArray,
        onError: (QUICTransportError) -> Nothing,
    ): QUICPacket.ShortHeader {
        val spinBit = flags and SHORT_HEADER_SPIN_BIT == SHORT_HEADER_SPIN_BIT
        val reservedBits = (flags and SHORT_HEADER_RESERVED_BITS).toInt() ushr 3
        val keyPhaseEncrypted = flags and SHORT_HEADER_KEY_PHASE == SHORT_HEADER_KEY_PHASE

        val packetNumber = readAndDecodePacketNumber(
            bytes = bytes,
            packetNumberLengthEncrypted = flags and SHORT_HEADER_PACKET_NUMBER_LENGTH,
            largestPacketNumberInSpace = -2, // todo
            onError = onError
        )

        return OneRTTPacket_v1(
            destinationConnectionId = destinationConnectionID,
            spinBit = spinBit,
            reservedBits = reservedBits,
            keyPhase = keyPhaseEncrypted, // todo decode?
            packetNumber = packetNumber,
            payload = bytes
        )
    }

    private inline fun readAndDecodePacketNumber(
        bytes: ByteReadPacket,
        packetNumberLengthEncrypted: UInt8,
        largestPacketNumberInSpace: Long,
        onError: (QUICTransportError) -> Nothing,
    ): Long {
        val packetNumberLength = Crypto.decryptPacketNumberLength(
            encrypted = packetNumberLengthEncrypted,
            onError = onError
        )

        if (packetNumberLength.toLong() > bytes.remaining) {
            onError(PROTOCOL_VIOLATION)
        }

        val packetNumberEncrypted = when (packetNumberLength) {
            0u -> bytes.readUInt8().toUInt32()
            1u -> bytes.readUInt16().toUInt32()
            2u -> bytes.readUInt24()
            3u -> bytes.readUInt32()
            else -> unreachable()
        }

        val packetNumberEncoded = Crypto.decryptPacketNumber(packetNumberEncrypted, onError)

        return decodePacketNumber(
            largestPn = largestPacketNumberInSpace,
            truncatedPn = packetNumberEncoded,
            pnLen = packetNumberLength
        )
    }
}
