/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.recovery.*
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.core.*

internal typealias FrameWriteFunction = suspend FrameWriter.() -> Unit

internal interface PacketSendHandler {
    /**
     * @param expectedFrameSize - Expected size of the frame when it will be written into a packet.
     * It should not be less than the actual size, but it can be bigger.
     *
     * @return Packet number of the packet to which the frame was written
     */
    suspend fun writeFrame(expectedFrameSize: Int, handler: BytePacketBuilder.() -> Unit): Long
}

internal sealed class PacketSendHandlerImpl<ReadyHandler : ReadyPacketHandler>(
    protected val packetHandler: ReadyHandler,
    protected val encryptionLevel: EncryptionLevel,
    private val role: ConnectionRole,
    private val connection: ConnectionForRetransmission,
) : PacketSendHandler {
    protected abstract val logger: Logger

    private val buffer = MutexPacketBuilder()

    private val retransmission = RetransmissionImpl(connection)
    private val recovery = Recovery(retransmission)

    private val frameWriter: FrameWriter by lazy {
        FrameWriterImpl(this, recovery).also {
            retransmission.withFrameWriter(it)
        }
    }

    protected abstract val maximumHeaderSize: Int

    private var currentPacketNumber: Long = packetHandler.getPacketNumber(encryptionLevel)

    suspend fun writeFrame(handler: FrameWriteFunction) {
        frameWriter.handler()
    }

    override suspend fun writeFrame(
        expectedFrameSize: Int,
        handler: BytePacketBuilder.() -> Unit,
    ): Long = buffer.withLock { buffer ->
        val expectedPacketSize: Int =
            buffer.size +
                packetHandler.usedDatagramSize +
                maximumHeaderSize +
                expectedFrameSize +
                PayloadSize.ENCRYPTION_HEADER_LENGTH

        // send an already pending packet as this one does not fit into datagram size limits
        if (expectedPacketSize > packetHandler.maxUdpPayloadSize) {
            logger.info("Frame exceeded max_udp_payload_size of ${packetHandler.maxUdpPayloadSize}, flushing datagram")
            finish(getPacketPayloadNonBlocking(), getAndNextPacketNumber())

            packetHandler.forceEndDatagram()
        }

        buffer.handler()
        currentPacketNumber
    }

    suspend fun finish() {
        var packetNumber: Long = 0
        val payload = buffer.flush {
            // hare we are in lock for buffer, so we can call getAndNextPacketNumber
            packetNumber = getAndNextPacketNumber()
        }.readBytes()

        finish(payload, packetNumber)
    }

    private suspend fun finish(payload: ByteArray, packetNumber: Long) {
        if (payload.isNotEmpty()) {
            packetHandler.withDatagramBuilder { builder ->
                sendPacket(builder, payload, packetNumber)
            }
        }
    }

    protected abstract suspend fun sendPacket(
        datagramBuilder: BytePacketBuilder,
        payload: ByteArray,
        packetNumber: Long
    )

    private fun getPacketPayloadNonBlocking(): ByteArray {
        return buffer.flushNonBlocking().readBytes()
    }

    /**
     * Should only be called with lock for [buffer]
     */
    private fun getAndNextPacketNumber(): Long {
        return currentPacketNumber.also {
            currentPacketNumber = packetHandler.getPacketNumber(encryptionLevel)
        }
    }

    class Initial(
        private val packetWriter: PacketWriter,
        packetHandler: ReadyPacketHandler.Initial,
        role: ConnectionRole,
        connection: ConnectionForRetransmission,
    ) : PacketSendHandlerImpl<ReadyPacketHandler.Initial>(packetHandler, EncryptionLevel.Initial, role, connection) {
        override val logger: Logger = logger()

        override val maximumHeaderSize: Int by lazy {
            PayloadSize.ofByteArrayWithLength(packetHandler.destinationConnectionIDSize) +
                PayloadSize.ofByteArrayWithLength(packetHandler.sourceConnectionIDSize) +
                PayloadSize.ofByteArrayWithLength(packetHandler.token.size) +
                PayloadSize.HEADER_FLAGS_LENGTH +
                PayloadSize.LONG_HEADER_VERSION_LENGTH +
                PayloadSize.LONG_HEADER_LENGTH_FIELD_MAX_LENGTH +
                PayloadSize.HEADER_PACKET_NUMBER_MAX_LENGTH
        }

        override suspend fun sendPacket(datagramBuilder: BytePacketBuilder, payload: ByteArray, packetNumber: Long) {
            packetWriter.writeInitialPacket(
                largestAcknowledged = packetHandler.getLargestAcknowledged(encryptionLevel),
                packetBuilder = datagramBuilder,
                version = QUICVersion.V1,
                destinationConnectionID = packetHandler.destinationConnectionID,
                sourceConnectionID = packetHandler.sourceConnectionID,
                packetNumber = packetNumber,
                token = packetHandler.token,
                payload = payload,
            )
        }
    }

    class Handshake(
        private val packetWriter: PacketWriter,
        packetHandler: ReadyPacketHandler,
        role: ConnectionRole,
        connection: ConnectionForRetransmission,
    ) : PacketSendHandlerImpl<ReadyPacketHandler>(packetHandler, EncryptionLevel.Handshake, role, connection) {
        override val logger: Logger = logger()

        override val maximumHeaderSize: Int by lazy {
            PayloadSize.ofByteArrayWithLength(packetHandler.destinationConnectionIDSize) +
                PayloadSize.ofByteArrayWithLength(packetHandler.sourceConnectionIDSize) +
                PayloadSize.HEADER_FLAGS_LENGTH +
                PayloadSize.LONG_HEADER_VERSION_LENGTH +
                PayloadSize.LONG_HEADER_LENGTH_FIELD_MAX_LENGTH +
                PayloadSize.HEADER_PACKET_NUMBER_MAX_LENGTH
        }

        override suspend fun sendPacket(datagramBuilder: BytePacketBuilder, payload: ByteArray, packetNumber: Long) {
            packetWriter.writeHandshakePacket(
                largestAcknowledged = packetHandler.getLargestAcknowledged(encryptionLevel),
                packetBuilder = datagramBuilder,
                version = QUICVersion.V1,
                destinationConnectionID = packetHandler.destinationConnectionID,
                sourceConnectionID = packetHandler.sourceConnectionID,
                packetNumber = packetNumber,
                payload = payload,
            )
        }
    }

    class OneRTT(
        private val packetWriter: PacketWriter,
        packetHandler: ReadyPacketHandler.OneRTT,
        role: ConnectionRole,
        connection: ConnectionForRetransmission,
    ) : PacketSendHandlerImpl<ReadyPacketHandler.OneRTT>(packetHandler, EncryptionLevel.AppData, role, connection) {
        override val logger: Logger = logger()

        override val maximumHeaderSize: Int by lazy {
            packetHandler.destinationConnectionIDSize +
                PayloadSize.HEADER_FLAGS_LENGTH +
                PayloadSize.HEADER_PACKET_NUMBER_MAX_LENGTH
        }

        override suspend fun sendPacket(datagramBuilder: BytePacketBuilder, payload: ByteArray, packetNumber: Long) {
            packetWriter.writeOneRTTPacket(
                largestAcknowledged = packetHandler.getLargestAcknowledged(encryptionLevel),
                packetBuilder = datagramBuilder,
                spinBit = packetHandler.spinBit,
                keyPhase = packetHandler.keyPhase,
                destinationConnectionID = packetHandler.destinationConnectionID,
                packetNumber = packetNumber,
                payload = payload,
            )
        }
    }
}
