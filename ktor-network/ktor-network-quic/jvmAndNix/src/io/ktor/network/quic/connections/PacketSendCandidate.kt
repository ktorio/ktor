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

internal typealias FrameWriteFunction = FrameWriter.(
    builder: BytePacketBuilder,
    hookConsumer: (hook: (Long) -> Unit) -> Unit,
) -> Unit

// todo handle dividing long contents
internal sealed class PacketSendCandidate(
    private val hasPayload: Boolean = true,
    private val datagramUsedSize: () -> Int = { 0 },
    private val onPacketPayloadReady: suspend (payload: (Long) -> ByteArray) -> Unit,
) {
    private val buffer = LockablePacketBuilder()
    private val packetNumberHooks = mutableListOf<(Long) -> Unit>()

    fun writeFrame(write: FrameWriteFunction) = buffer.withLock {
        write(FrameWriterImpl, it) { hook ->
            packetNumberHooks.add(hook)
        }
    }

    suspend fun finish(lastPacketInDatagram: Boolean = false) {
        onPacketPayloadReady { packetNumber ->
            packetNumberHooks.forEach { hook -> hook(packetNumber) }

            if (!hasPayload) EMPTY_BYTE_ARRAY else buffer.flush { buffer ->
                if (lastPacketInDatagram) {
                    buffer.writeFully(
                        src = PADDING_SAMPLE,
                        offset = 0,
                        length = maxOf(MIN_PACKET_SIZE_IN_BYTES - buffer.size - datagramUsedSize(), 0)
                    )
                }
            }.readBytes()
        }
    }

    companion object {
        private const val MIN_PACKET_SIZE_IN_BYTES = 1200

        private val PADDING_SAMPLE = ByteArray(1200) { 0x00 }
    }

    class Initial(
        tlsComponent: TLSComponent,
        packetHandler: ReadyPacketHandler.Initial,
    ) : PacketSendCandidate(
        datagramUsedSize = { packetHandler.usedDatagramSize },
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
    ) : PacketSendCandidate(
        datagramUsedSize = { packetHandler.usedDatagramSize },
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
    ) : PacketSendCandidate(
        datagramUsedSize = { packetHandler.usedDatagramSize },
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
    ) : PacketSendCandidate(
        hasPayload = false,
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
    ) : PacketSendCandidate(
        hasPayload = false,
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
