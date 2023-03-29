/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName", "unused", "UNUSED_PARAMETER")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.HeaderProtectionUtils.HP_FLAGS_LONG_MASK
import io.ktor.network.quic.packets.HeaderProtectionUtils.HP_FLAGS_SHORT_MASK
import io.ktor.network.quic.packets.HeaderProtectionUtils.flagsHPMask
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask1
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask2
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask3
import io.ktor.network.quic.packets.HeaderProtectionUtils.pnHPMask4
import io.ktor.network.quic.tls.*
import io.ktor.utils.io.core.*

internal object PacketWriter {
    /**
     * Writes a Version Negotiation packet to [packetBuilder] according to specification.
     * If it is QUIC version 1, sets second bit to 1.
     * (as fixed bit for protocol multiplexing [RFC-7983](https://www.rfc-editor.org/rfc/rfc7983.html))
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-version-negotiation-packet)
     */
    fun writeVersionNegotiationPacket(
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        supportedVersions: Array<UInt32>,
    ) = with(packetBuilder) {
        @Suppress("KotlinConstantConditions")
        val first: UInt8 = when (version) {
            QUICVersion.V1 -> 0xC0u // header type bit + fixed bit
            else -> 0x80u // only header type bit
        }

        writeUInt8(first)
        writeUInt32(version)
        writeConnectionID(destinationConnectionID)
        writeConnectionID(sourceConnectionID)
        supportedVersions.forEach {
            writeUInt32(it)
        }
    }

    /**
     * header form = 0b1 (long header)
     * fixed bit = 0b1
     * packet type = 0b11 (retry type)
     * unused = 0b0000
     */
    private const val RETRY_PACKET_FIRST_BYTE: UInt8 = 0xF0u

    /**
     * Writes Retry Packet to [packetBuilder] according to specification.
     * Computes integrity tag based on the contents of packet
     * (see [QUIC-TLS](https://www.rfc-editor.org/rfc/rfc9001#name-retry-packet-integrity))
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-retry-packet)
     */
    fun writeRetryPacket(
        packetBuilder: BytePacketBuilder,
        originalDestinationConnectionID: ConnectionID,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        retryToken: ByteArray,
    ) {
        checkVersionConstraints(version, destinationConnectionID, sourceConnectionID)

        val pseudoPacket = buildPacket {
            writeConnectionID(originalDestinationConnectionID)
            writeUInt8(RETRY_PACKET_FIRST_BYTE)
            writeUInt32(version)
            writeConnectionID(destinationConnectionID)
            writeConnectionID(sourceConnectionID)
            writeFully(retryToken)
        }.readBytes()

        val integrityTag: ByteArray = computeRetryIntegrityTag(pseudoPacket)

        // debug only
        RetryPacket_v1(
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            retryToken = retryToken,
            retryIntegrityTag = integrityTag,
        ).apply(::debugLog)

        // skip originalDestinationConnectionID and it's length
        packetBuilder.writeFully(pseudoPacket, offset = 1 + originalDestinationConnectionID.size)
        packetBuilder.writeFully(integrityTag)
    }

    private fun computeRetryIntegrityTag(pseudoPacket: ByteArray): ByteArray {
        TODO("crypto")
    }

    /**
     * Writes Initial Packet to [packetBuilder] according to specification.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-retry-packet)
     */
    suspend inline fun writeInitialPacket(
        tlsComponent: TLSComponent,
        largestAcknowledged: Long,
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        token: ByteArray,
        packetNumber: Long,
        payload: ByteArray,
    ) {
        // debug only
        InitialPacket_v1(
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            token = token,
            packetNumber = packetNumber,
            payload = ByteReadPacket(payload)
        ).apply(::debugLog)

        writeLongHeaderPacket_v1(
            tlsComponent = tlsComponent,
            encryptionLevel = EncryptionLevel.Initial,
            largestAcknowledged = largestAcknowledged,
            packetBuilder = packetBuilder,
            packetType = PktConst.LONG_HEADER_PACKET_TYPE_INITIAL,
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            packetNumber = packetNumber,
            payload = payload,
            writeBeforeLength = {
                writeVarInt(token.size)
                writeFully(token)
            }
        )
    }

    /**
     * Writes Handshake Packet to [packetBuilder] according to specification.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-handshake-packet)
     */
    suspend inline fun writeHandshakePacket(
        tlsComponent: TLSComponent,
        largestAcknowledged: Long,
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        packetNumber: Long,
        payload: ByteArray,
    ) {
        // debug only
        HandshakePacket_v1(
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            packetNumber = packetNumber,
            payload = ByteReadPacket(payload)
        ).apply(::debugLog)

        writeLongHeaderPacket_v1(
            tlsComponent = tlsComponent,
            encryptionLevel = EncryptionLevel.Handshake,
            largestAcknowledged = largestAcknowledged,
            packetBuilder = packetBuilder,
            packetType = PktConst.LONG_HEADER_PACKET_TYPE_HANDSHAKE,
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            packetNumber = packetNumber,
            payload = payload,
        )
    }

    /**
     * Writes 0-RTT Packet to [packetBuilder] according to specification.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-0-rtt)
     */
    suspend inline fun writeZeroRTTPacket(
        tlsComponent: TLSComponent,
        largestAcknowledged: Long,
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        packetNumber: Long,
        payload: ByteArray,
    ) {
        // debug only
        ZeroRTTPacket_v1(
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            packetNumber = packetNumber,
            payload = ByteReadPacket(payload)
        ).apply(::debugLog)

        writeLongHeaderPacket_v1(
            tlsComponent = tlsComponent,
            encryptionLevel = EncryptionLevel.AppData,
            largestAcknowledged = largestAcknowledged,
            packetBuilder = packetBuilder,
            packetType = PktConst.LONG_HEADER_PACKET_TYPE_0_RTT,
            version = version,
            destinationConnectionID = destinationConnectionID,
            sourceConnectionID = sourceConnectionID,
            packetNumber = packetNumber,
            payload = payload,
        )
    }

    /**
     * header form = 0b1 (long header)
     * fixed bit = 0b1
     * reserved bits = 0b00
     */
    private const val LONG_HEADER_FIRST_BYTE_TEMPLATE: UInt8 = 0xC0u

    private suspend inline fun writeLongHeaderPacket_v1(
        tlsComponent: TLSComponent,
        encryptionLevel: EncryptionLevel,
        largestAcknowledged: Long,
        packetBuilder: BytePacketBuilder,
        packetType: UInt8,
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
        packetNumber: Long,
        payload: ByteArray,
        writeBeforeLength: BytePacketBuilder.() -> Unit = {},
    ) {
        checkVersionConstraints(version, destinationConnectionID, sourceConnectionID)

        val packetNumberLength: UInt8 = getPacketNumberLength(packetNumber, largestAcknowledged)
        val flags: UInt8 = LONG_HEADER_FIRST_BYTE_TEMPLATE or packetType or packetNumberLength

        val unencryptedHeader = buildPacket {
            writeUInt8(flags)
            writeUInt32(version)
            writeConnectionID(destinationConnectionID)
            writeConnectionID(sourceConnectionID)
            writeBeforeLength()
            writeVarInt(payload.size)
            writeRawPacketNumber(this, packetNumberLength, packetNumber.toUInt())
        }.readBytes()

        withEncryptedPayloadAndHPMask(
            tlsComponent = tlsComponent,
            packetNumber = packetNumber,
            level = encryptionLevel,
            unencryptedHeader = unencryptedHeader,
            unencryptedPayload = payload,
        ) { encryptedPayload, headerProtectionMask ->
            with(packetBuilder) {
                writeUInt8(flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_LONG_MASK))
                writeUInt32(version)
                writeConnectionID(destinationConnectionID)
                writeConnectionID(sourceConnectionID)
                writeBeforeLength()
                writeVarInt(encryptedPayload.size)
                encryptAndWritePacketNumber(packetNumber, packetNumberLength, headerProtectionMask)
                writeFully(encryptedPayload)
            }
        }
    }

    /**
     * header form = 0b0 (short header)
     * fixed bit = 0b1
     * reserved bits = 0b00
     */
    private const val SHORT_HEADER_FIRST_BYTE_TEMPLATE: UInt8 = 0x40u

    /**
     * Writes 1-RTT Packet to [packetBuilder] according to specification.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-1-rtt-packet)
     */
    suspend fun writeOneRTTPacket(
        tlsComponent: TLSComponent,
        largestAcknowledged: Long,
        packetBuilder: BytePacketBuilder,
        spinBit: Boolean,
        keyPhase: Boolean,
        destinationConnectionID: ConnectionID,
        packetNumber: Long,
        payload: ByteArray,
    ) {
        // debug only
        OneRTTPacket_v1(
            destinationConnectionID = destinationConnectionID,
            spinBit = spinBit,
            keyPhase = keyPhase,
            packetNumber = packetNumber,
            payload = ByteReadPacket(payload),
        ).apply(::debugLog)

        val packetNumberLength: UInt8 = getPacketNumberLength(packetNumber, largestAcknowledged)

        val spinBitValue: UInt8 = if (spinBit) PktConst.SHORT_HEADER_SPIN_BIT else 0x00u
        val keyPhaseValue: UInt8 = if (keyPhase) PktConst.SHORT_HEADER_KEY_PHASE else 0x00u

        val flags: UInt8 = SHORT_HEADER_FIRST_BYTE_TEMPLATE or
            spinBitValue or
            keyPhaseValue or
            packetNumberLength

        val unencryptedHeader = buildPacket {
            writeUInt8(flags)
            writeFully(destinationConnectionID.value)
            writeRawPacketNumber(this, packetNumberLength, packetNumber.toUInt())
        }.readBytes()

        withEncryptedPayloadAndHPMask(
            tlsComponent = tlsComponent,
            packetNumber = packetNumber,
            level = EncryptionLevel.AppData,
            unencryptedHeader = unencryptedHeader,
            unencryptedPayload = payload,
        ) { encryptedPayload, headerProtectionMask ->
            with(packetBuilder) {
                writeUInt8(flags xor flagsHPMask(headerProtectionMask, HP_FLAGS_SHORT_MASK))
                writeFully(destinationConnectionID.value) // no length as it should be known for the connection
                encryptAndWritePacketNumber(packetNumber, packetNumberLength, headerProtectionMask)
                writeFully(encryptedPayload)
            }
        }
    }

    private fun BytePacketBuilder.encryptAndWritePacketNumber(
        packetNumber: Long,
        packetNumberLength: UInt8,
        headerProtectionMask: Long,
    ) {
        // write packet number and encrypt it with the header protection mask
        // see: https://www.rfc-editor.org/rfc/rfc9001#name-header-protection-applicati
        when (packetNumberLength.toUInt32()) {
            1u -> writeUInt8(packetNumber.toUByte() xor pnHPMask1(headerProtectionMask))
            2u -> writeUInt16(packetNumber.toUShort() xor pnHPMask2(headerProtectionMask))
            3u -> writeUInt24(packetNumber.toUInt() xor pnHPMask3(headerProtectionMask))
            4u -> writeUInt32(packetNumber.toUInt() xor pnHPMask4(headerProtectionMask))
        }
    }

    /**
     * Execute payload, encrypt it and calculate headerProtectionMask based on the sample from the encrypted payload
     */
    private suspend inline fun withEncryptedPayloadAndHPMask(
        tlsComponent: TLSComponent,
        level: EncryptionLevel,
        packetNumber: Long,
        unencryptedHeader: ByteArray,
        unencryptedPayload: ByteArray,
        body: (encryptedPayload: ByteArray, headerProtectionMask: Long) -> Unit,
    ) {
        val encryptedPayload: ByteArray = tlsComponent.encrypt(
            payload = unencryptedPayload,
            associatedData = unencryptedHeader,
            packetNumber = packetNumber,
            level = level
        )

        require(encryptedPayload.size >= PktConst.HP_SAMPLE_LENGTH) {
            "Payload should at least ${PktConst.HP_SAMPLE_LENGTH} bytes to encrypt header"
        }

        val sample: ByteArray = encryptedPayload.copyOfRange(0, PktConst.HP_SAMPLE_LENGTH)
        val headerProtectionMask: Long = tlsComponent.headerProtectionMask(sample, level, isDecrypting = false)

        return body(encryptedPayload, headerProtectionMask)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun BytePacketBuilder.writeConnectionID(connectionID: ConnectionID) {
        writeUInt8(connectionID.size.toUByte())
        writeFully(connectionID.value)
    }

    private fun checkVersionConstraints(
        version: UInt32,
        destinationConnectionID: ConnectionID,
        sourceConnectionID: ConnectionID,
    ) {
        checkCIDLength(version, destinationConnectionID) {
            "DCID length must be less then $it in QUIC version $version"
        }
        checkCIDLength(version, sourceConnectionID) {
            "SCID length must be less then $it in QUIC version $version"
        }
    }

    private inline fun checkCIDLength(
        version: UInt32,
        connectionID: ConnectionID,
        message: (UInt8) -> String,
    ) {
        val maxCIDLength: UInt8 = MaxCIDLength.fromVersion(version) { error("unknown version: $version") }
        require(connectionID.size.toUByte() <= maxCIDLength) { message(maxCIDLength) }
    }

    fun writeRawPacketNumber(builder: BytePacketBuilder, length: UByte, number: UInt32) = with(builder) {
        when (length.toInt()) {
            0 -> writeUInt8(number.toUByte())
            1 -> writeUInt16(number.toUShort())
            2 -> writeUInt24(number)
            3 -> writeUInt32(number)
        }
    }

    private fun debugLog(packet: QUICPacket) {
        println("Writing packet:")
        println(packet.toDebugString(withPayload = false))
    }
}
