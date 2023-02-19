/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.errors.TransportError_v1.*
import io.ktor.network.quic.packets.HeaderProtection.HP_FLAGS_LONG_MASK
import io.ktor.network.quic.packets.HeaderProtection.HP_FLAGS_SHORT_MASK
import io.ktor.network.quic.packets.HeaderProtection.flagsHPMask
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask1
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask2
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask3
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask4
import io.ktor.network.quic.util.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import kotlin.properties.*

internal object PacketReader {
    private const val HEADER_TYPE: UInt8 = 0x80u
    private const val FIXED_BIT: UInt8 = 0x40u

    private const val LONG_HEADER_PACKET_TYPE: UInt8 = 0x30u

    private const val LONG_HEADER_RESERVED_BITS: UInt8 = 0x0Cu
    private const val LONG_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u

    private const val SHORT_HEADER_RESERVED_BITS: UInt8 = 0x18u
    private const val SHORT_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u


    /**
     * Reads a single QUIC packet.
     *
     * @param negotiatedVersion - used for the short headers.
     * Version of the protocol that is used for this connection.
     *
     * @param dcidLength - used for the short headers
     * Length of the destinationConnectionID parameter that is used for this connection
     */
    inline fun readSinglePacket(
        bytes: ByteReadPacket,
        negotiatedVersion: UInt32,
        dcidLength: UInt8,
        raiseError: (QUICTransportError) -> Nothing,
    ): QUICPacket {
        val flags: UInt8 = bytes.readUInt8 { raiseError(PROTOCOL_VIOLATION) }

        val headerProtectionKey = "" // todo crypto

        if (flags and HEADER_TYPE == HEADER_TYPE) { // Long Header bit is set
            // Version independent properties of packets with the Long Header

            val version: UInt32 = bytes.readUInt32 { raiseError(PROTOCOL_VIOLATION) }

            // Connection ID max size may vary between versions
            val maxCIDLength: UInt8 = MaxCIDLength.fromVersion(version) { raiseError(FRAME_ENCODING_ERROR) }

            val destinationConnectionID: ByteArray = readConnectionID(bytes, maxCIDLength, raiseError)
            val sourceConnectionID: ByteArray = readConnectionID(bytes, maxCIDLength, raiseError)

            // End of version independent properties

            if (version == QUICVersion.VersionNegotiation) {
                return readVersionNegotiationPacket(bytes, destinationConnectionID, sourceConnectionID, raiseError)
            }

            return readLongHeader_v1(
                bytes = bytes,
                headerProtectionKey = headerProtectionKey,
                flags = flags,
                version = version,
                destinationConnectionID = destinationConnectionID,
                sourceConnectionID = sourceConnectionID,
                raiseError = raiseError
            )
        } else { // Short Header bit is set
            val maxCIDLength: UInt8 = MaxCIDLength.fromVersion(negotiatedVersion) { raiseError(FRAME_ENCODING_ERROR) }

            if (dcidLength > maxCIDLength) {
                raiseError(PROTOCOL_VIOLATION)
            }

            // Version independent properties of packets with the Short Header

            val destinationConnectionID: ByteArray = bytes.readBytes(dcidLength.toInt())

            // End of version independent properties

            return readShortHeader_v1(
                bytes = bytes,
                headerProtectionKey = headerProtectionKey,
                flags = flags,
                destinationConnectionID = destinationConnectionID,
                raiseError = raiseError
            )
        }
    }

    private inline fun readConnectionID(
        bytes: ByteReadPacket,
        maxCIDLength: UInt8,
        raiseError: (QUICTransportError) -> Nothing,
    ): ByteArray {
        val connectionIDLength: UInt8 = bytes.readUInt8 { raiseError(PROTOCOL_VIOLATION) }
        if (connectionIDLength > maxCIDLength) {
            raiseError(PROTOCOL_VIOLATION)
        }
        return bytes.readBytes(connectionIDLength.toInt())
    }

    private inline fun readVersionNegotiationPacket(
        bytes: ByteReadPacket,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        raiseError: (QUICTransportError) -> Nothing,
    ): VersionNegotiationPacket {
        // supportedVersions is an array of 32-bit integers with no specified length
        if (bytes.remaining % 4 != 0L) {
            raiseError(PROTOCOL_VIOLATION)
        }

        val supportedVersions = Array((bytes.remaining / 4).toInt()) { bytes.readInt() }

        return VersionNegotiationPacket(destinationConnectionID, sourceConnectionID, supportedVersions)
    }

    /**
     * Reads a packet with a Long Header as it specified in QUIC version 1
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets)
     */
    private inline fun readLongHeader_v1(
        bytes: ByteReadPacket,
        headerProtectionKey: String,
        flags: UInt8,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        raiseError: (QUICTransportError) -> Nothing,
    ): QUICPacket.LongHeader {
        // The next bit (0x40) of byte 0 is set to 1, unless the packet is a Version Negotiation packet.
        // Packets containing a zero value for this bit are not valid packets in this version and MUST be discarded.
        if (flags and FIXED_BIT != FIXED_BIT) {
            raiseError(PROTOCOL_VIOLATION)
        }

        val type = when (flags and LONG_HEADER_PACKET_TYPE) {
            PktConst.LONG_HEADER_PACKET_TYPE_INITIAL -> PacketType_v1.Initial
            PktConst.LONG_HEADER_PACKET_TYPE_0_RTT -> PacketType_v1.ZeroRTT
            PktConst.LONG_HEADER_PACKET_TYPE_HANDSHAKE -> PacketType_v1.Handshake
            PktConst.LONG_HEADER_PACKET_TYPE_RETRY -> PacketType_v1.Retry
            else -> unreachable()
        }

        return when (type) {
            PacketType_v1.Initial, PacketType_v1.ZeroRTT, PacketType_v1.Handshake -> {
                var token: ByteArray? = null
                if (type == PacketType_v1.Initial) {
                    val tokenLength = bytes.readVarIntOrElse { raiseError(PROTOCOL_VIOLATION) }
                    token = bytes.readBytes(tokenLength.toInt())
                }

                val length: Long = bytes.readVarIntOrElse { raiseError(PROTOCOL_VIOLATION) }

                val headerProtectionMask: Long = getHeaderProtectionMask(bytes, headerProtectionKey, raiseError)
                val decodedFlags: UInt8 = flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_LONG_MASK)

                val packetNumber: Long = readAndDecodePacketNumber(
                    bytes = bytes,
                    headerProtectionMask = headerProtectionMask,
                    packetNumberLength = decodedFlags and LONG_HEADER_PACKET_NUMBER_LENGTH,
                    largestPacketNumberInSpace = -1, // todo
                )

                val reservedBits: Int = (decodedFlags and LONG_HEADER_RESERVED_BITS).toInt() ushr 2

                val payload = readAndDecryptPacketPayload(bytes, length)

                when (type) {
                    PacketType_v1.Initial -> InitialPacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        token = token!!,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    PacketType_v1.ZeroRTT -> ZeroRTTPacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    PacketType_v1.Handshake -> HandshakePacket_v1(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        reservedBits = reservedBits,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    else -> unreachable()
                }
            }

            PacketType_v1.Retry -> {
                val retryTokenLength = bytes.remaining - PktConst.RETRY_PACKET_INTEGRITY_TAG_LENGTH
                if (retryTokenLength < 0) {
                    raiseError(PROTOCOL_VIOLATION)
                }
                val retryToken = bytes.readBytes(retryTokenLength.toInt())
                val integrityTag = bytes.readBytes(PktConst.RETRY_PACKET_INTEGRITY_TAG_LENGTH)

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
        headerProtectionKey: String,
        flags: UInt8,
        destinationConnectionID: ByteArray,
        raiseError: (QUICTransportError) -> Nothing,
    ): QUICPacket.ShortHeader {
        val headerProtectionMask: Long = getHeaderProtectionMask(bytes, headerProtectionKey, raiseError)
        val decodedFlags: UInt8 = flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_SHORT_MASK)

        val packetNumber: Long = readAndDecodePacketNumber(
            bytes = bytes,
            headerProtectionMask = headerProtectionMask,
            packetNumberLength = decodedFlags and SHORT_HEADER_PACKET_NUMBER_LENGTH,
            largestPacketNumberInSpace = -2, // todo
        )

        val spinBit: Boolean = decodedFlags and PktConst.SHORT_HEADER_SPIN_BIT == PktConst.SHORT_HEADER_SPIN_BIT
        val reservedBits: Int = (decodedFlags and SHORT_HEADER_RESERVED_BITS).toInt() ushr 3
        val keyPhase: Boolean = decodedFlags and PktConst.SHORT_HEADER_KEY_PHASE == PktConst.SHORT_HEADER_KEY_PHASE

        return OneRTTPacket_v1(
            destinationConnectionId = destinationConnectionID,
            spinBit = spinBit,
            reservedBits = reservedBits,
            keyPhase = keyPhase,
            packetNumber = packetNumber,
            payload = readAndDecryptPacketPayload(bytes, bytes.remaining)
        )
    }

    private fun readAndDecryptPacketPayload(bytes: ByteReadPacket, length: Long): ByteReadPacket {
        TODO("crypto")
    }

    /**
     * Reads sample from packet's payload and uses it to remove header protection
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9001#name-header-protection-sample)
     */
    private inline fun getHeaderProtectionMask(
        bytes: ByteReadPacket,
        headerProtectionKey: String,
        raiseError: (QUICTransportError) -> Nothing,
    ): Long {
        if (bytes.remaining < 132) { // 4 bytes - max packet number size, 128 bytes - sample
            raiseError(PROTOCOL_VIOLATION)
        }

        val array = ByteArray(128)
        bytes.peekTo(Memory.of(array), destinationOffset = 0, offset = 4)

        return HeaderProtection.headerProtection(headerProtectionKey, array)
    }

    private fun readAndDecodePacketNumber(
        bytes: ByteReadPacket,
        packetNumberLength: UInt8,
        headerProtectionMask: Long,
        largestPacketNumberInSpace: Long,
    ): Long {
        // read packet number and decrypt it with the header protection mask
        // see: https://www.rfc-editor.org/rfc/rfc9001#name-header-protection-applicati
        val packetNumberEncoded: UInt32 = when (packetNumberLength.toInt()) {
            0 -> (bytes.readUInt8() xor pnHPMask1(headerProtectionMask)).toUInt32()
            1 -> (bytes.readUInt16() xor pnHPMask2(headerProtectionMask)).toUInt32()
            2 -> bytes.readUInt24() xor pnHPMask3(headerProtectionMask)
            3 -> bytes.readUInt32() xor pnHPMask4(headerProtectionMask)
            else -> unreachable()
        }

        return decodePacketNumber(
            largestPn = largestPacketNumberInSpace,
            truncatedPn = packetNumberEncoded,
            pnLen = packetNumberLength.toUInt32(),
        )
    }
}
