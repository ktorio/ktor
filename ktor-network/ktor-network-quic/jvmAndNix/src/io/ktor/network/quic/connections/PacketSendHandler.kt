/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.core.*

internal typealias FrameWriteFunction = suspend FrameWriter.(
    builder: BytePacketBuilder,
    hookConsumer: (hook: (Long) -> Unit) -> Unit,
) -> Unit

internal sealed class PacketSendHandler(
    private val hasPayload: Boolean = true,
    private val packetHandler: ReadyPacketHandler,
    type: QUICPacketType,
    private val onPacketPayloadReady: suspend (payload: (Long) -> ByteArray) -> Unit,
) {
    protected abstract val logger: Logger

    private val buffer = MutexPacketBuilder()
    private val packetNumberHooks = mutableListOf<(Long) -> Unit>()

    private val maximumHeaderSize: Int by lazy {
        when (type) {
            QUICPacketType.Initial -> {
                byteArrayFrameSize(packetHandler.destinationConnectionIDSize) +
                    byteArrayFrameSize(packetHandler.sourceConnectionIDSize) +
                    byteArrayFrameSize((packetHandler as ReadyPacketHandler.Initial).token.size) +
                    PktConst.HEADER_FLAGS_LENGTH +
                    PktConst.LONG_HEADER_VERSION_LENGTH +
                    PktConst.LONG_HEADER_LENGTH_FIELD_MAX_LENGTH +
                    PktConst.HEADER_PACKET_NUMBER_MAX_LENGTH
            }

            QUICPacketType.OneRTT ->
                packetHandler.destinationConnectionIDSize +
                    PktConst.HEADER_FLAGS_LENGTH +
                    PktConst.HEADER_PACKET_NUMBER_MAX_LENGTH

            QUICPacketType.Retry -> TODO("Not yet implemented")
            QUICPacketType.VersionNegotiation -> TODO("Not yet implemented")

            // Handshake, 0-RTT
            QUICPacketType.Handshake, QUICPacketType.ZeroRTT -> {
                byteArrayFrameSize(packetHandler.destinationConnectionIDSize) +
                    byteArrayFrameSize(packetHandler.sourceConnectionIDSize) +
                    PktConst.HEADER_FLAGS_LENGTH +
                    PktConst.LONG_HEADER_VERSION_LENGTH +
                    PktConst.LONG_HEADER_LENGTH_FIELD_MAX_LENGTH +
                    PktConst.HEADER_PACKET_NUMBER_MAX_LENGTH
            }
        }
    }

    suspend fun writeFrame(write: FrameWriteFunction) = buffer.withLock { buffer ->
        val temp = buildPacket {
            write(FrameWriterImpl, this) { hook ->
                packetNumberHooks.add(hook)
            }
        }.readBytes()

        val usedSize = buffer.size +
            temp.size +
            maximumHeaderSize +
            packetHandler.usedDatagramSize +
            PktConst.ENCRYPTION_HEADER_LENGTH

        // send an already pending packet as this one does not fit into datagram size limits
        if (usedSize > packetHandler.maxUdpPayloadSize) {
            logger.info("Frame exceeded max_udp_payload_size of ${packetHandler.maxUdpPayloadSize}, flushing datagram")
            finish(getPacketPayloadNonBlocking())

            packetHandler.forceEndDatagram()
        }

        buffer.writeFully(temp)
    }

    suspend fun finish() {
        finish(getPacketPayload())
    }

    private suspend fun finish(payload: ByteArray) {
        if (payload.isNotEmpty() || !hasPayload) {
            onPacketPayloadReady { packetNumber ->
                packetNumberHooks.forEach { hook -> hook(packetNumber) }

                payload
            }
        }
    }

    private suspend fun getPacketPayload(): ByteArray = if (!hasPayload) {
        EMPTY_BYTE_ARRAY
    } else {
        buffer.flush().readBytes()
    }

    private fun getPacketPayloadNonBlocking(): ByteArray = if (!hasPayload) {
        EMPTY_BYTE_ARRAY
    } else {
        buffer.flushNonBlocking().readBytes()
    }

    class Initial(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler.Initial,
    ) : PacketSendHandler(
        type = QUICPacketType.Initial,
        packetHandler = packetHandler,
        onPacketPayloadReady = { payload ->
            val packetNumber = packetHandler.getPacketNumber(EncryptionLevel.Initial)

            packetHandler.withDatagramBuilder { datagramBuilder ->
                PacketWriter.writeInitialPacket(
                    tlsComponent = tlsComponent,
                    largestAcknowledged = packetHandler.getLargestAcknowledged(EncryptionLevel.Initial),
                    packetBuilder = datagramBuilder,
                    version = QUICVersion.V1,
                    destinationConnectionID = packetHandler.destinationConnectionID,
                    sourceConnectionID = packetHandler.sourceConnectionID,
                    packetNumber = packetNumber,
                    token = packetHandler.token,
                    payload = payload(packetNumber),
                )
            }
        }
    ) {
        override val logger: Logger = logger()
    }

    class Handshake(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler,
    ) : PacketSendHandler(
        type = QUICPacketType.Handshake,
        packetHandler = packetHandler,
        onPacketPayloadReady = { payload ->
            val packetNumber = packetHandler.getPacketNumber(EncryptionLevel.Handshake)

            packetHandler.withDatagramBuilder { datagramBuilder ->
                PacketWriter.writeHandshakePacket(
                    tlsComponent = tlsComponent,
                    largestAcknowledged = packetHandler.getLargestAcknowledged(EncryptionLevel.Handshake),
                    packetBuilder = datagramBuilder,
                    version = QUICVersion.V1,
                    destinationConnectionID = packetHandler.destinationConnectionID,
                    sourceConnectionID = packetHandler.sourceConnectionID,
                    packetNumber = packetNumber,
                    payload = payload(packetNumber),
                )
            }
        }
    ) {
        override val logger: Logger = logger()
    }

    class OneRTT(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler.OneRTT,
    ) : PacketSendHandler(
        type = QUICPacketType.OneRTT,
        packetHandler = packetHandler,
        onPacketPayloadReady = { payload ->
            val packetNumber = packetHandler.getPacketNumber(EncryptionLevel.AppData)

            packetHandler.withDatagramBuilder { datagramBuilder ->
                PacketWriter.writeOneRTTPacket(
                    tlsComponent = tlsComponent,
                    largestAcknowledged = packetHandler.getLargestAcknowledged(EncryptionLevel.AppData),
                    packetBuilder = datagramBuilder,
                    spinBit = packetHandler.spinBit,
                    keyPhase = packetHandler.keyPhase,
                    destinationConnectionID = packetHandler.destinationConnectionID,
                    packetNumber = packetNumber,
                    payload = payload(packetNumber),
                )
            }
        }
    ) {
        override val logger: Logger = logger()
    }

    @Suppress("unused")
    class VersionNegotiation(
        packetHandler: ReadyPacketHandler.VersionNegotiation,
    ) : PacketSendHandler(
        type = QUICPacketType.VersionNegotiation,
        hasPayload = false,
        packetHandler = packetHandler,
        onPacketPayloadReady = { _ ->
            packetHandler.withDatagramBuilder { datagramBuilder ->
                PacketWriter.writeVersionNegotiationPacket(
                    packetBuilder = datagramBuilder,
                    version = QUICVersion.V1,
                    destinationConnectionID = packetHandler.destinationConnectionID,
                    sourceConnectionID = packetHandler.sourceConnectionID,
                    supportedVersions = packetHandler.supportedVersions
                )
            }
        }
    ) {
        override val logger: Logger = logger()
    }

    @Suppress("unused")
    class Retry(
        packetHandler: ReadyPacketHandler.Retry,
    ) : PacketSendHandler(
        type = QUICPacketType.Retry,
        hasPayload = false,
        packetHandler = packetHandler,
        onPacketPayloadReady = { _ ->
            packetHandler.withDatagramBuilder { datagramBuilder ->
                PacketWriter.writeRetryPacket(
                    packetBuilder = datagramBuilder,
                    version = QUICVersion.V1,
                    destinationConnectionID = packetHandler.destinationConnectionID,
                    sourceConnectionID = packetHandler.sourceConnectionID,
                    originalDestinationConnectionID = packetHandler.originalDestinationConnectionID,
                    retryToken = packetHandler.retryToken,
                )
            }
        }
    ) {
        override val logger: Logger = logger()
    }
}
