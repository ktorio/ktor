/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")
@file:OptIn(ExperimentalUnsignedTypes::class)

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.errors.QUICProtocolTransportError.*
import io.ktor.network.quic.packets.HeaderProtectionUtils.HP_FLAGS_LONG_MASK
import io.ktor.network.quic.packets.HeaderProtectionUtils.HP_FLAGS_SHORT_MASK
import io.ktor.network.quic.packets.HeaderProtectionUtils.flagsHPMask
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask1
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask2
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask3
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask4
import io.ktor.network.quic.packets.PktConst.HP_SAMPLE_LENGTH
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*

internal object PacketReader {
    private val logger = logger()

    private const val HEADER_TYPE: UInt8 = 0x80u
    private const val FIXED_BIT: UInt8 = 0x40u

    private const val LONG_HEADER_PACKET_TYPE: UInt8 = 0x30u

    private const val LONG_HEADER_RESERVED_BITS: UInt8 = 0x0Cu
    private const val LONG_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u

    private const val SHORT_HEADER_RESERVED_BITS: UInt8 = 0x18u
    private const val SHORT_HEADER_PACKET_NUMBER_LENGTH: UInt8 = 0x03u

    /**
     * Reads a single QUIC packet. Handles its decryption
     */
    suspend fun readSinglePacket(
        bytes: ByteReadPacket,
        firstDcidInDatagram: QUICConnectionID?,
        matchConnection: suspend (
            destinationCID: QUICConnectionID,
            sourceCID: QUICConnectionID?,
            packetType: QUICPacketType,
        ) -> QUICConnection,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): QUICPacket? {
        val flags: UInt8 = bytes.readUInt8 { raiseError(PACKET_END) }

        if (flags and HEADER_TYPE == HEADER_TYPE) { // Long Header bit is set
            // Version independent properties of packets with the Long Header

            val version: UInt32 = bytes.readUInt32 { raiseError(PACKET_END) }

            // Connection ID max size may vary between versions
            val maxCIDLength: UInt8 = MaxCIDLength.fromVersion(version) { raiseError(FRAME_ENCODING_ERROR) }

            val destinationConnectionID: QUICConnectionID = readConnectionID(bytes, maxCIDLength, raiseError)

            // Senders MUST NOT coalesce QUIC packets for different connections into a single UDP datagram.
            // Receivers SHOULD ignore any subsequent packets with a different Destination Connection ID
            // than the first packet in the datagram.
            if (firstDcidInDatagram != null && destinationConnectionID != firstDcidInDatagram) {
                return null
            }

            val sourceConnectionID: QUICConnectionID = readConnectionID(bytes, maxCIDLength, raiseError)

            // End of version independent properties

            if (version == QUICVersion.VersionNegotiation) {
                return readVersionNegotiationPacket(bytes, destinationConnectionID, sourceConnectionID, raiseError)
            }

            val type = when (flags and LONG_HEADER_PACKET_TYPE) {
                PktConst.LONG_HEADER_PACKET_TYPE_INITIAL -> QUICPacketType.Initial
                PktConst.LONG_HEADER_PACKET_TYPE_0_RTT -> QUICPacketType.ZeroRTT
                PktConst.LONG_HEADER_PACKET_TYPE_HANDSHAKE -> QUICPacketType.Handshake
                PktConst.LONG_HEADER_PACKET_TYPE_RETRY -> QUICPacketType.Retry
                else -> unreachable()
            }

            val connection = matchConnection(destinationConnectionID, sourceConnectionID, type)

            return readLongHeader(
                bytes = bytes,
                type = type,
                connection = connection,
                flags = flags,
                version = version,
                destinationConnectionID = destinationConnectionID,
                sourceConnectionID = sourceConnectionID,
                raiseError = raiseError
            )
        } else { // Short Header bit is set
            // Version independent properties of packets with the Short Header

            if (bytes.remaining < QUICConnectionID.endpointSCIDLength) {
                raiseError(PACKET_END)
            }

            val destinationConnectionID: QUICConnectionID = bytes.readBytes(QUICConnectionID.endpointSCIDLength).asCID()

            // Senders MUST NOT coalesce QUIC packets for different connections into a single UDP datagram.
            // Receivers SHOULD ignore any subsequent packets with a different Destination Connection ID
            // than the first packet in the datagram.
            if (firstDcidInDatagram != null && destinationConnectionID != firstDcidInDatagram) {
                return null
            }

            // End of version independent properties

            val connection = matchConnection(destinationConnectionID, null, QUICPacketType.OneRTT)

            return readShortHeader(
                bytes = bytes,
                connection = connection,
                flags = flags,
                destinationConnectionID = destinationConnectionID,
                raiseError = raiseError
            )
        }
    }

    private suspend fun readConnectionID(
        bytes: ByteReadPacket,
        maxCIDLength: UInt8,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): QUICConnectionID {
        val connectionIDLength: UInt8 = bytes.readUInt8 { raiseError(PACKET_END) }
        if (connectionIDLength > maxCIDLength) {
            raiseError(PROTOCOL_VIOLATION.withReason("Actual CID length exceeds protocol's maximum"))
        }
        if (bytes.remaining < connectionIDLength.toInt()) {
            raiseError(PACKET_END)
        }
        return bytes.readBytes(connectionIDLength.toInt()).asCID()
    }

    private suspend fun readVersionNegotiationPacket(
        bytes: ByteReadPacket,
        destinationConnectionID: QUICConnectionID,
        sourceConnectionID: QUICConnectionID,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): QUICVersionNegotiationPacket {
        // supportedVersions is an array of 32-bit integers with no specified length
        if (bytes.remaining % 4 != 0L) {
            raiseError(PROTOCOL_VIOLATION.withReason("Malformed supportedVersions array"))
        }

        val supportedVersions = Array((bytes.remaining / 4).toInt()) { bytes.readInt() }

        return QUICVersionNegotiationPacket(destinationConnectionID, sourceConnectionID, supportedVersions)
    }

    /**
     * Reads a packet with a Long Header as it specified in QUIC version 1
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-long-header-packets)
     */
    private suspend fun readLongHeader(
        bytes: ByteReadPacket,
        type: QUICPacketType,
        connection: QUICConnection,
        flags: UInt8,
        version: UInt32,
        destinationConnectionID: QUICConnectionID,
        sourceConnectionID: QUICConnectionID,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): QUICPacket.LongHeader {
        // The next bit (0x40) of byte 0 is set to 1, unless the packet is a Version Negotiation packet.
        // Packets containing a zero value for this bit are not valid packets in this version and MUST be discarded.
        if (flags and FIXED_BIT != FIXED_BIT) {
            raiseError(PROTOCOL_VIOLATION.withReason("Fixed bit is 0"))
        }

        return when (type) {
            QUICPacketType.Initial, QUICPacketType.ZeroRTT, QUICPacketType.Handshake -> {
                var token: ByteArray? = null
                if (type == QUICPacketType.Initial) {
                    val tokenLength = bytes.readVarIntOrElse { raiseError(PACKET_END) }
                    if (bytes.remaining < tokenLength) {
                        raiseError(PACKET_END)
                    }
                    token = bytes.readBytes(tokenLength.toInt())
                }

                val nextByte = bytes.tryPeek()
                if (nextByte == -1) {
                    raiseError(PACKET_END)
                }

                // :NOTE: apparently, quiche uses the minimum of 2 bytes to store 'length' value
                val lengthOfLength = 1 shl (nextByte ushr 6)
                val length: Long = bytes.readVarIntOrElse { raiseError(PACKET_END) }

                val encryptionLevel = when (type) {
                    QUICPacketType.Initial -> EncryptionLevel.Initial
                    QUICPacketType.Handshake -> EncryptionLevel.Handshake
                    QUICPacketType.ZeroRTT -> error("unsupported")
                    else -> unreachable()
                }

                logger.info("Packet type: $type")

                val headerProtectionMask: Long = getHeaderProtectionMask(
                    bytes = bytes,
                    tlsComponent = connection.tlsComponent,
                    level = encryptionLevel,
                    raiseError = raiseError
                )

                val decodedFlags: UInt8 = flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_LONG_MASK)

                val packetNumberLength: UInt32 = (decodedFlags and LONG_HEADER_PACKET_NUMBER_LENGTH) + 1u

                val rawPacketNumber: UInt32 = readAndDecodePacketNumber(
                    bytes = bytes,
                    headerProtectionMask = headerProtectionMask,
                    packetNumberLength = packetNumberLength,
                )

                val packetNumber = decodePacketNumber(
                    largestPn = connection.packetNumberSpacePool[encryptionLevel].largestPacketNumber,
                    truncatedPn = rawPacketNumber,
                    pnLen = packetNumberLength,
                )

                val reservedBits: Int = (decodedFlags and LONG_HEADER_RESERVED_BITS).toInt() ushr 2

                val associatedData = buildPacket {
                    writeUByte(decodedFlags)
                    writeUInt(version)
                    writeConnectionID(destinationConnectionID)
                    writeConnectionID(sourceConnectionID)
                    if (type == QUICPacketType.Initial) {
                        writeVarInt(token!!.size)
                        writeFully(token)
                    }
                    writeVarInt(length, lengthOfLength)
                    PacketWriter.writeRawPacketNumber(this, packetNumberLength, rawPacketNumber)
                }.readBytes()

                val payload = readAndDecryptPacketPayload(
                    tlsComponent = connection.tlsComponent,
                    associatedData = associatedData,
                    bytes = bytes,
                    length = (length - packetNumberLength.toLong()).toInt(),
                    packetNumber = packetNumber,
                    level = encryptionLevel,
                    raiseError = raiseError,
                )

                if (reservedBits != 0) {
                    raiseError(PROTOCOL_VIOLATION.withReason("Reserved bits are not 00"))
                }

                when (type) {
                    QUICPacketType.Initial -> QUICInitialPacket(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        token = token!!,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    QUICPacketType.ZeroRTT -> QUICZeroRTTPacket(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    QUICPacketType.Handshake -> QUICHandshakePacket(
                        version = version,
                        destinationConnectionID = destinationConnectionID,
                        sourceConnectionID = sourceConnectionID,
                        packetNumber = packetNumber,
                        payload = payload,
                    )

                    else -> unreachable()
                }
            }

            QUICPacketType.Retry -> {
                val retryTokenLength = bytes.remaining - PktConst.RETRY_PACKET_INTEGRITY_TAG_LENGTH
                if (retryTokenLength < 0) {
                    raiseError(PROTOCOL_VIOLATION.withReason("Negative token length"))
                }
                val retryToken = bytes.readBytes(retryTokenLength.toInt())
                val integrityTag = bytes.readBytes(PktConst.RETRY_PACKET_INTEGRITY_TAG_LENGTH)

                QUICRetryPacket(version, destinationConnectionID, sourceConnectionID, retryToken, integrityTag)
            }

            else -> unreachable()
        }
    }

    /**
     * Reads a packet with a Short Header as it specified in QUIC version 1
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-short-header-packets)
     */
    private suspend fun readShortHeader(
        bytes: ByteReadPacket,
        connection: QUICConnection,
        flags: UInt8,
        destinationConnectionID: QUICConnectionID,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): QUICPacket.ShortHeader {
        val headerProtectionMask: Long = getHeaderProtectionMask(
            bytes = bytes,
            tlsComponent = connection.tlsComponent,
            level = EncryptionLevel.AppData,
            raiseError = raiseError
        )

        val decodedFlags: UInt8 = flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_SHORT_MASK)

        val packetNumberLength: UInt32 = (decodedFlags and SHORT_HEADER_PACKET_NUMBER_LENGTH) + 1u

        val rawPacketNumber: UInt32 = readAndDecodePacketNumber(
            bytes = bytes,
            headerProtectionMask = headerProtectionMask,
            packetNumberLength = packetNumberLength,
        )

        val packetNumber = decodePacketNumber(
            largestPn = connection.packetNumberSpacePool[EncryptionLevel.AppData].largestPacketNumber,
            truncatedPn = rawPacketNumber,
            pnLen = packetNumberLength,
        )

        val spinBit: Boolean = decodedFlags and PktConst.SHORT_HEADER_SPIN_BIT == PktConst.SHORT_HEADER_SPIN_BIT
        val reservedBits: Int = (decodedFlags and SHORT_HEADER_RESERVED_BITS).toInt() ushr 3
        val keyPhase: Boolean = decodedFlags and PktConst.SHORT_HEADER_KEY_PHASE == PktConst.SHORT_HEADER_KEY_PHASE

        val associatedData = buildPacket {
            writeUByte(decodedFlags)
            writeFully(destinationConnectionID.value)
            PacketWriter.writeRawPacketNumber(this, packetNumberLength, rawPacketNumber)
        }.readBytes()

        val payload = readAndDecryptPacketPayload(
            tlsComponent = connection.tlsComponent,
            associatedData = associatedData,
            bytes = bytes,
            length = bytes.remaining.toInt(),
            packetNumber = packetNumber,
            level = EncryptionLevel.AppData,
            raiseError = raiseError,
        )

        if (reservedBits != 0) {
            raiseError(PROTOCOL_VIOLATION.withReason("Reserved bits are not 00"))
        }

        return QUICOneRTTPacket(
            destinationConnectionID = destinationConnectionID,
            spinBit = spinBit,
            keyPhase = keyPhase,
            packetNumber = packetNumber,
            payload = payload,
        )
    }

    private suspend fun readAndDecryptPacketPayload(
        tlsComponent: TLSComponent,
        associatedData: ByteArray,
        bytes: ByteReadPacket,
        length: Int,
        packetNumber: Long,
        level: EncryptionLevel,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): ByteReadPacket {
        if (bytes.remaining < length) {
            raiseError(PACKET_END)
        }

        return ByteReadPacket(tlsComponent.decrypt(bytes.readBytes(length), associatedData, packetNumber, level))
    }

    /**
     * Reads sample from packet's payload and uses it to remove header protection
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9001#name-header-protection-sample)
     */
    private suspend fun getHeaderProtectionMask(
        bytes: ByteReadPacket,
        tlsComponent: TLSComponent,
        level: EncryptionLevel,
        raiseError: suspend (QUICTransportError) -> Nothing,
    ): Long {
        if (bytes.remaining < 20) { // 4 bytes - max packet number size, 16 bytes - sample
            raiseError(PACKET_END)
        }

        val array = ByteArray(HP_SAMPLE_LENGTH)
        array.useMemory(0, array.size) {
            bytes.peekTo(it, destinationOffset = 0, offset = 4)
        }

        return tlsComponent.headerProtectionMask(array, level, isDecrypting = true)
    }

    private fun readAndDecodePacketNumber(
        bytes: ByteReadPacket,
        packetNumberLength: UInt32,
        headerProtectionMask: Long,
    ): UInt32 {
        // read packet number and decrypt it with the header protection mask
        // see: https://www.rfc-editor.org/rfc/rfc9001#name-header-protection-applicati
        return when (packetNumberLength) {
            1u -> (bytes.readUInt8() xor pnHPMask1(headerProtectionMask)).toUInt32()
            2u -> (bytes.readUInt16() xor pnHPMask2(headerProtectionMask)).toUInt32()
            3u -> bytes.readUInt24() xor pnHPMask3(headerProtectionMask)
            4u -> bytes.readUInt32() xor pnHPMask4(headerProtectionMask)
            else -> unreachable()
        }
    }

    private val PACKET_END: QUICTransportError = PROTOCOL_VIOLATION.withReason("End of the packet reached")
}
