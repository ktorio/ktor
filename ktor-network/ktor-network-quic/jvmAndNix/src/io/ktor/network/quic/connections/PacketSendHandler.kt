/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.tls.*
import io.ktor.utils.io.core.*

internal typealias FrameWriteFunction = suspend FrameWriter.(
    builder: BytePacketBuilder,
    hookConsumer: (hook: (Long) -> Unit) -> Unit,
) -> Unit

internal sealed class PacketSendHandler(
    private val hasPayload: Boolean = true,
    private val packetHandler: ReadyPacketHandler,
    type: PacketType_v1,
    private val onPacketPayloadReady: suspend (payload: (Long) -> ByteArray) -> Unit,
) {
    private val buffer = MutexPacketBuilder()
    private val packetNumberHooks = mutableListOf<(Long) -> Unit>()

    private val maximumHeaderSize: Int by lazy {
        when (type) {
            PacketType_v1.Initial -> {
                byteArrayFrameSize(packetHandler.destinationConnectionIDSize) +
                    byteArrayFrameSize(packetHandler.sourceConnectionIDSize) +
                    byteArrayFrameSize((packetHandler as ReadyPacketHandler.Initial).token.size) +
                    1 + 4 + 4 + 4 // flags, version, max length, max packet number
            }

            PacketType_v1.OneRTT -> packetHandler.destinationConnectionIDSize + 1 + 4 // flags, max packet number

            PacketType_v1.Retry -> TODO("Not yet implemented")
            PacketType_v1.VersionNegotiation -> TODO("Not yet implemented")

            // Handshake, 0-RTT
            PacketType_v1.Handshake, PacketType_v1.ZeroRTT -> {
                byteArrayFrameSize(packetHandler.destinationConnectionIDSize) +
                    byteArrayFrameSize(packetHandler.sourceConnectionIDSize) +
                    1 + 4 + 4 + 4 // flags, version, max length, max packet number
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
            println("[PacketSendHandler] Frame exceeded max_udp_payload_size of ${packetHandler.maxUdpPayloadSize}, flushing datagram") // ktlint-disable max-line-length
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

    private suspend fun getPacketPayload(): ByteArray = if (!hasPayload) EMPTY_BYTE_ARRAY else {
        buffer.flush().readBytes()
    }

    private fun getPacketPayloadNonBlocking(): ByteArray = if (!hasPayload) EMPTY_BYTE_ARRAY else {
        buffer.flushNonBlocking().readBytes()
    }

    class Initial(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler.Initial,
    ) : PacketSendHandler(
        type = PacketType_v1.Initial,
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
    )

    class Handshake(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler,
    ) : PacketSendHandler(
        type = PacketType_v1.Handshake,
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
    )

    class OneRTT(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler.OneRTT,
    ) : PacketSendHandler(
        type = PacketType_v1.OneRTT,
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
    )

    @Suppress("unused")
    class VersionNegotiation(
        packetHandler: ReadyPacketHandler.VersionNegotiation,
    ) : PacketSendHandler(
        type = PacketType_v1.VersionNegotiation,
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
    )

    @Suppress("unused")
    class Retry(
        packetHandler: ReadyPacketHandler.Retry,
    ) : PacketSendHandler(
        type = PacketType_v1.Retry,
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
    )
}
