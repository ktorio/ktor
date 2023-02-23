/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName", "unused", "UNUSED_PARAMETER")

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.HeaderProtection.HP_FLAGS_LONG_MASK
import io.ktor.network.quic.packets.HeaderProtection.HP_FLAGS_SHORT_MASK
import io.ktor.network.quic.packets.HeaderProtection.flagsHPMask
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask1
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask2
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask3
import io.ktor.network.quic.packets.HeaderProtection.pnHPMask4
import io.ktor.utils.io.core.*

internal object PacketWriter {
    /**
     * Writes Version Negotiation packet to [packetBuilder] according to specification.
     * If it is QUIC version 1, sets second bit to 1.
     * (as fixed bit for protocol multiplexing [RFC-7983](https://www.rfc-editor.org/rfc/rfc7983.html))
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-version-negotiation-packet)
     */
    @OptIn(ExperimentalUnsignedTypes::class)
    fun writeVersionNegotiationPacket(
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        vararg supportedVersions: UInt32,
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
        originalDestinationConnectionID: ByteArray,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
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
    inline fun writeInitialPacket(
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        token: ByteArray,
        packetNumber: Long,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
    ) {
        writeLongHeaderPacket_v1(
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
    inline fun writeHandshakePacket(
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        packetNumber: Long,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
    ) {
        writeLongHeaderPacket_v1(
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
    inline fun writeZeroRTTPacket(
        packetBuilder: BytePacketBuilder,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        packetNumber: Long,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
    ) {
        writeLongHeaderPacket_v1(
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

    private inline fun writeLongHeaderPacket_v1(
        packetBuilder: BytePacketBuilder,
        packetType: UInt8,
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
        packetNumber: Long,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
        writeBeforeLength: BytePacketBuilder.() -> Unit = {},
    ) {
        checkVersionConstraints(version, destinationConnectionID, sourceConnectionID)
        val headerProtectionKey = "" // todo crypto

        withEncryptedPayloadAndHPMask(headerProtectionKey, payload) { encryptedPayload, headerProtectionMask ->
            val packetNumberLength: UInt8 = getPacketNumberLength(packetNumber, largestAcked = -1 /* todo */)
            val first: UInt8 = LONG_HEADER_FIRST_BYTE_TEMPLATE or packetType or packetNumberLength

            with(packetBuilder) {
                writeUInt8(first xor flagsHPMask(headerProtectionMask, HP_FLAGS_LONG_MASK))
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
    fun writeOneRTTPacket(
        packetBuilder: BytePacketBuilder,
        spinBit: Boolean,
        keyPhase: Boolean,
        destinationConnectionID: ByteArray,
        packetNumber: Long,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
    ) {
        val headerProtectionKey = " " // todo crypto

        withEncryptedPayloadAndHPMask(headerProtectionKey, payload) { encryptedPayload, headerProtectionMask ->
            val packetNumberLength: UInt8 = getPacketNumberLength(packetNumber, largestAcked = -1 /* todo */)

            val spinBitValue: UInt8 = if (spinBit) PktConst.SHORT_HEADER_SPIN_BIT else 0x00u
            val keyPhaseValue: UInt8 = if (keyPhase) PktConst.SHORT_HEADER_KEY_PHASE else 0x00u

            val first: UInt8 = SHORT_HEADER_FIRST_BYTE_TEMPLATE or
                spinBitValue or
                keyPhaseValue or
                packetNumberLength

            with(packetBuilder) {
                writeUInt8(first xor flagsHPMask(headerProtectionMask, HP_FLAGS_SHORT_MASK))
                writeFully(destinationConnectionID) // no length as it should be known for the connection
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
    private inline fun withEncryptedPayloadAndHPMask(
        headerProtectionKey: String,
        payload: FrameWriter.(BytePacketBuilder) -> Unit,
        body: (encryptedPayload: ByteArray, headerProtectionMask: Long) -> Unit,
    ) {
        val unencryptedPayload: ByteArray = buildPacket {
            payload(FrameWriterImpl, this)
        }.readBytes()

        val encryptedPayload: ByteArray = encryptPacketPayload(unencryptedPayload)

        require(encryptedPayload.size >= PktConst.HP_SAMPLE_LENGTH) {
            "Payload should at least ${PktConst.HP_SAMPLE_LENGTH} bytes to encrypt header"
        }

        val sample: ByteArray = encryptedPayload.copyOfRange(0, PktConst.HP_SAMPLE_LENGTH)
        val headerProtectionMask: Long = HeaderProtection.headerProtection(headerProtectionKey, sample)

        return body(encryptedPayload, headerProtectionMask)
    }

    private fun encryptPacketPayload(payload: ByteArray): ByteArray {
        TODO("crypto")
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun BytePacketBuilder.writeConnectionID(connectionID: ByteArray) {
        writeUInt8(connectionID.size.toUByte())
        writeFully(connectionID)
    }

    private fun checkVersionConstraints(
        version: UInt32,
        destinationConnectionID: ByteArray,
        sourceConnectionID: ByteArray,
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
        connectionID: ByteArray,
        message: (UInt8) -> String,
    ) {
        val maxCIDLength: UInt8 = MaxCIDLength.fromVersion(version) { error("unknown version: $version") }
        require(connectionID.size.toUByte() <= maxCIDLength) { message(maxCIDLength) }
    }
}
