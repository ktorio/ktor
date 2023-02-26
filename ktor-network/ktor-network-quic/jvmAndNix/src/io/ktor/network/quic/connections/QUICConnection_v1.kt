/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.connections

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*

internal class QUICConnection_v1(
    private val isServer: Boolean,
    private var initialFrameToken: ByteArray,
    initialLocalConnectionID: ConnectionID,
    initialPeerConnectionID: ConnectionID,
    private val localTransportParameters: TransportParameters,
    private val peerTransportParameters: TransportParameters,
    private val connectionIDLength: Int,
) {
    private val processor = PayloadProcessor()

    private val localConnectionIDs = ConnectionIDRecordList(localTransportParameters.active_connection_id_limit)
    private val peerConnectionIDs = ConnectionIDRecordList(peerTransportParameters.active_connection_id_limit)

    private var peerMaxData: Long = peerTransportParameters.initial_max_data
    private var localMaxData: Long = localTransportParameters.initial_max_data

    private var localMaxStreamsBidirectional: Long = localTransportParameters.initial_max_streams_bidi
    private var peerMaxStreamsBidirectional: Long = peerTransportParameters.initial_max_streams_bidi

    private var localMaxStreamsUnidirectional: Long = localTransportParameters.initial_max_streams_uni
    private var peerMaxStreamsUnidirectional: Long = peerTransportParameters.initial_max_streams_uni

    private val maxStreamData = hashMapOf<Long, Long>()

    init {
        val initialLocalSequenceNumber = localTransportParameters.preferred_address?.let { 1L } ?: 0
        val initialPeerSequenceNumber = peerTransportParameters.preferred_address?.let { 1L } ?: 0

        localConnectionIDs.add(ConnectionIDRecord(initialLocalConnectionID, initialLocalSequenceNumber))
        peerConnectionIDs.add(ConnectionIDRecord(initialPeerConnectionID, initialPeerSequenceNumber))
    }

    suspend fun terminate() {
        TODO()
    }

    fun acceptsConnectionID(connectionID: ConnectionID): Boolean {
        return localConnectionIDs[connectionID] != null
    }

    suspend fun processShortHeaderPacket(packet: QUICPacket) {
        TODO()
    }

    private suspend inline fun send(frames: FrameWriter.(BytePacketBuilder, QUICPacket) -> Unit) {
        // todo keep this as small as possible
    }

    private inner class PayloadProcessor : FrameProcessor {
        override suspend fun acceptPadding(packet: QUICPacket): QUICTransportError_v1? {
            return null
        }

        override suspend fun acceptPing(packet: QUICPacket): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptACK(
            packet: QUICPacket,
            ackDelay: Long,
            ackRanges: LongArray,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptACKWithECN(
            packet: QUICPacket,
            ackDelay: Long,
            ackRanges: LongArray,
            ect0: Long,
            ect1: Long,
            ectCE: Long,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptResetStream(
            packet: QUICPacket,
            streamId: Long,
            applicationProtocolErrorCode: AppError,
            finalSize: Long,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptStopSending(
            packet: QUICPacket,
            streamId: Long,
            applicationProtocolErrorCode: AppError,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptCrypto(
            packet: QUICPacket,
            offset: Long,
            cryptoData: ByteArray,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptNewToken(packet: QUICPacket, token: ByteArray): QUICTransportError_v1? {
            if (isServer) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            initialFrameToken = token

            return null
        }

        override suspend fun acceptStream(
            packet: QUICPacket,
            streamId: Long,
            offset: Long,
            fin: Boolean,
            streamData: ByteArray,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptMaxData(packet: QUICPacket, maximumData: Long): QUICTransportError_v1? {
            peerMaxData = maximumData.coerceAtLeast(peerMaxData)

            return null
        }

        override suspend fun acceptMaxStreamData(
            packet: QUICPacket,
            streamId: Long,
            maximumStreamData: Long,
        ): QUICTransportError_v1? {
            maxStreamData[streamId] = maximumStreamData.coerceAtLeast(maxStreamData[streamId] ?: 0)

            // todo check receive-only and not created streams

            return null
        }

        override suspend fun acceptMaxStreamsBidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            peerMaxStreamsBidirectional = maximumStreams.coerceAtLeast(peerMaxStreamsBidirectional)

            return null
        }

        override suspend fun acceptMaxStreamsUnidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            peerMaxStreamsUnidirectional = maximumStreams.coerceAtLeast(peerMaxStreamsUnidirectional)

            return null
        }

        override suspend fun acceptDataBlocked(packet: QUICPacket, maximumData: Long): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptStreamDataBlocked(
            packet: QUICPacket,
            streamId: Long,
            maximumStreamData: Long,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptStreamsBlockedBidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptStreamsBlockedUnidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptNewConnectionId(
            packet: QUICPacket,
            sequenceNumber: Long,
            retirePriorTo: Long,
            connectionID: ConnectionID,
            statelessResetToken: ByteArray?,
        ): QUICTransportError_v1? {
            if (connectionIDLength == 0) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            peerConnectionIDs[sequenceNumber]?.let {
                if (it.connectionID neq connectionID) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }
            }

            peerConnectionIDs[connectionID]?.let {
                if (it.sequenceNumber != sequenceNumber) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }

                if (it.resetToken != null && statelessResetToken != null && it.resetToken neq statelessResetToken) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }
            }

            if (peerConnectionIDs.isRemoved(sequenceNumber)) {
                return null
            }

            if (sequenceNumber < peerConnectionIDs.threshold) {
                send { builder, _ ->
                    writeRetireConnectionId(builder, sequenceNumber)
                }
                return null
            }

            val retired = peerConnectionIDs.removePriorToAndSetThreshold(retirePriorTo)

            val record = ConnectionIDRecord(connectionID, sequenceNumber, statelessResetToken)

            if (!peerConnectionIDs.add(record)) {
                return TransportError_v1.CONNECTION_ID_LIMIT_ERROR
            }

            // todo wait for ack frames, send in batches
            send { builder, _ ->
                retired.forEach { retireSequenceNumber ->
                    writeRetireConnectionId(builder, retireSequenceNumber)
                }
            }

            return null
        }

        override suspend fun acceptRetireConnectionId(
            packet: QUICPacket,
            sequenceNumber: Long,
        ): QUICTransportError_v1? {
            if (localConnectionIDs.threshold < sequenceNumber) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            if (localConnectionIDs[sequenceNumber]?.connectionID eq packet.destinationConnectionID) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            if (connectionIDLength == 0) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            localConnectionIDs.remove(sequenceNumber)

            return null
        }

        override suspend fun acceptPathChallenge(packet: QUICPacket, data: ByteArray): QUICTransportError_v1? {
            send { builder, _ ->
                writePathResponse(builder, data)
            }

            return null
        }

        override suspend fun acceptPathResponse(packet: QUICPacket, data: ByteArray): QUICTransportError_v1? {
            // todo check data is eq to PATH_CHALLENGE

            TODO("Not yet implemented")
        }

        override suspend fun acceptConnectionCloseWithTransportError(
            packet: QUICPacket,
            errorCode: QUICTransportError_v1,
            frameType: FrameType_v1,
            reasonPhrase: ByteArray,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptConnectionCloseWithAppError(
            packet: QUICPacket,
            errorCode: AppError,
            reasonPhrase: ByteArray,
        ): QUICTransportError_v1? {
            TODO("Not yet implemented")
        }

        override suspend fun acceptHandshakeDone(packet: QUICPacket): QUICTransportError_v1? {
            if (isServer) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            TODO("Not yet implemented")
        }
    }
}
