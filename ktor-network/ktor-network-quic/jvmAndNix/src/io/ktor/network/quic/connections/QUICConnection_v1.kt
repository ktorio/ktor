/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.connections

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.tls.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.sync.*

/**
 * This class represents [QUIC connection](https://www.rfc-editor.org/rfc/rfc9000.html#name-connections).
 *
 * It manages connection's state, transport parameters, processing and sending packets and flow control.
 *
 * This class is used for already established connections, the handshake process is managed by todo
 */
@Suppress("CanBeParameter", "UNUSED_PARAMETER")
internal class QUICConnection_v1(
    val tlsComponent: TLSComponent,
    private val isServer: Boolean,

    /**
     * Initial CID used by this endpoint during the handshake.
     */
    private val initialLocalConnectionID: ConnectionID,

    /**
     * Initial CID used by the peer during the handshake.
     */
    private val initialPeerConnectionID: ConnectionID,

    /**
     * Negotiated length of the CIDs used during this connection
     */
    private val connectionIDLength: Int,
) {
    private val processor = PayloadProcessor()

    /**
     * [TransportParameters] that were announced to the peer.
     *
     * Peer should comply with them and can request some changes during the connection if needed.
     */
    private var localTransportParameters: TransportParameters = transportParameters()

    /**
     * [TransportParameters] that were announced by the peer.
     *
     * This endpoint should comply with them and can request some changes during the connection if needed.
     */
    private var peerTransportParameters: TransportParameters = transportParameters()

    /**
     * Token to send in the header of an Initial packet for a future connection.
     */
    private var initialFrameToken: ByteArray? = null

    /**
     * Pool of CIDs which this endpoint willing to accept
     */
    private val localConnectionIDs by lazy { ConnectionIDRecordList(localTransportParameters.active_connection_id_limit) }

    /**
     * Pool of CIDs which the peer is willing to accept
     */
    private val peerConnectionIDs by lazy { ConnectionIDRecordList(peerTransportParameters.active_connection_id_limit) }

    private var peerMaxData: Long = peerTransportParameters.initial_max_data
    private var localMaxData: Long = localTransportParameters.initial_max_data

    private var localMaxStreamsBidirectional: Long = localTransportParameters.initial_max_streams_bidi
    private var peerMaxStreamsBidirectional: Long = peerTransportParameters.initial_max_streams_bidi

    private var localMaxStreamsUnidirectional: Long = localTransportParameters.initial_max_streams_uni
    private var peerMaxStreamsUnidirectional: Long = peerTransportParameters.initial_max_streams_uni

    /**
     * Map of Stream IDs to the corresponding MAX_STREAM_DATA parameters
     */
    private val maxStreamData = hashMapOf<Long, Long>()

    init {
        tlsComponent.onTransportParametersKnown { local, peer ->
            localTransportParameters = local
            peerTransportParameters = peer

            onTransportParametersKnown()
        }
    }

    private fun onTransportParametersKnown() {
        val initialLocalSequenceNumber = localTransportParameters.preferred_address?.let { 1L } ?: 0
        val initialPeerSequenceNumber = peerTransportParameters.preferred_address?.let { 1L } ?: 0

        localConnectionIDs.add(ConnectionIDRecord(initialLocalConnectionID, initialLocalSequenceNumber))
        peerConnectionIDs.add(ConnectionIDRecord(initialPeerConnectionID, initialPeerSequenceNumber))

        peerMaxData = peerTransportParameters.initial_max_data
        localMaxData = localTransportParameters.initial_max_data

        localMaxStreamsBidirectional = localTransportParameters.initial_max_streams_bidi
        peerMaxStreamsBidirectional = peerTransportParameters.initial_max_streams_bidi

        localMaxStreamsUnidirectional = localTransportParameters.initial_max_streams_uni
        peerMaxStreamsUnidirectional = peerTransportParameters.initial_max_streams_uni
    }

    /**
     * Sends frame(s) to the peer. Provides information about the packet which will contain the frame(s).
     *
     * todo: what about frames that exceeds the max_udp_size?
     */
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
            // A server MUST treat receipt of a NEW_TOKEN frame as a connection error of type PROTOCOL_VIOLATION
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
            // An endpoint that is sending packets with a zero-length Destination Connection ID
            // MUST treat receipt of a NEW_CONNECTION_ID frame as a connection error of type PROTOCOL_VIOLATION.
            if (connectionIDLength == 0) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            // If an endpoint receives a NEW_CONNECTION_ID frame
            // that repeats a previously issued connection ID with a different Stateless Reset Token field value
            // or a different Sequence Number field value,
            // or if a sequence number is used for different connection IDs,
            // the endpoint MAY treat that receipt as a connection error of type PROTOCOL_VIOLATION.
            peerConnectionIDs[sequenceNumber]?.let {
                if (it.connectionID neq connectionID) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }
            }

            peerConnectionIDs[connectionID]?.let {
                if (it.sequenceNumber != sequenceNumber) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }

                if (it.resetToken != null &&
                    statelessResetToken != null &&
                    !it.resetToken.contentEquals(statelessResetToken)
                ) {
                    return TransportError_v1.PROTOCOL_VIOLATION
                }
            }

            // An endpoint that receives a NEW_CONNECTION_ID frame
            // with a sequence number smaller than the Retire Prior To field
            // of a previously received NEW_CONNECTION_ID frame
            // MUST send a corresponding RETIRE_CONNECTION_ID frame that retires the newly received connection ID,
            // unless it has already done so for that sequence number.
            if (peerConnectionIDs.isRemoved(sequenceNumber)) {
                return null
            }

            if (sequenceNumber < peerConnectionIDs.threshold) {
                send { builder, _ ->
                    writeRetireConnectionId(builder, sequenceNumber)
                }
                return null
            }

            // Upon receipt of an increased Retire Prior To field,
            // the peer MUST stop using the corresponding connection IDs
            // and retire them with RETIRE_CONNECTION_ID frames before adding
            // the newly provided connection ID to the set of active connection IDs.
            val retired = peerConnectionIDs.removePriorToAndSetThreshold(retirePriorTo)

            // todo wait for ack frames, send in batches, cache new CID and proceed here
            send { builder, _ ->
                retired.forEach { retireSequenceNumber ->
                    writeRetireConnectionId(builder, retireSequenceNumber)
                }
            }

            val record = ConnectionIDRecord(connectionID, sequenceNumber, statelessResetToken)

            // After processing a NEW_CONNECTION_ID frame and adding and retiring active connection IDs,
            // if the number of active connection IDs exceeds the value
            // advertised in its active_connection_id_limit transport parameter,
            // an endpoint MUST close the connection with an error of type CONNECTION_ID_LIMIT_ERROR.
            if (!peerConnectionIDs.add(record)) {
                return TransportError_v1.CONNECTION_ID_LIMIT_ERROR
            }

            return null
        }

        override suspend fun acceptRetireConnectionId(
            packet: QUICPacket,
            sequenceNumber: Long,
        ): QUICTransportError_v1? {
            // Receipt of a RETIRE_CONNECTION_ID frame containing a sequence number greater than any previously sent
            // to the peer MUST be treated as a connection error of type PROTOCOL_VIOLATION.
            if (localConnectionIDs.threshold < sequenceNumber) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            // The sequence number specified in a RETIRE_CONNECTION_ID frame
            // MUST NOT refer to the Destination Connection ID field of the packet in which the frame is contained.
            // The peer MAY treat this as a connection error of type PROTOCOL_VIOLATION.
            if (localConnectionIDs[sequenceNumber]?.connectionID?.eq(packet.destinationConnectionID) == true) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            // An endpoint cannot send this frame if it was provided with a zero-length connection ID by its peer.
            // An endpoint that provides a zero-length connection ID
            // MUST treat receipt of a RETIRE_CONNECTION_ID frame as a connection error of type PROTOCOL_VIOLATION.
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
