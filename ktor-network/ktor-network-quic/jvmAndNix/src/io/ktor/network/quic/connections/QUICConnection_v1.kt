/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ClassName")

package io.ktor.network.quic.connections

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*

/**
 * This class represents [QUIC connection](https://www.rfc-editor.org/rfc/rfc9000.html#name-connections).
 *
 * It manages connection's state, transport parameters, processing and sending packets and flow control.
 *
 * This class is used for already established connections, the handshake process is managed by todo
 */
@Suppress("CanBeParameter")
internal class QUICConnection_v1(
    private val isServer: Boolean,

    /**
     * Initial CID used by this endpoint during the handshake.
     */
    private val initialLocalConnectionID: ConnectionID,

    /**
     * CID that was used by the peer as first Destination Connection ID.
     */
    private val originalDestinationConnectionID: ConnectionID,

    /**
     * Initial CID used by the peer during the handshake.
     */
    private val initialPeerConnectionID: ConnectionID,

    /**
     * Negotiated length of the CIDs used during this connection
     */
    private val connectionIDLength: Int,

    private val outgoingDatagramChannel: SendChannel<Datagram>,
    initialSocketAddress: SocketAddress,
    tlsComponentProvider: (ProtocolCommunicationProvider) -> TLSServerComponent,
) {
    /**
     * Negotiated QUIC version that is used during this connection
     */
    val version: UInt32 = QUICVersion.V1

    /**
     * Underlying TLS Component that handles encryption and handshake during this connection
     */
    val tlsComponent = tlsComponentProvider(ProtocolCommunicationProviderImpl())

    /**
     * Peer's [SocketAddress]. Can change during connection via connection migration.
     */
    private var peerSocketAddress: SocketAddress = initialSocketAddress

    private val maxCIDLength = MaxCIDLength.fromVersion(QUICVersion.V1) { unreachable() }

    /**
     * Holds packet number spaces - one for each encryption level.
     *
     * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-packet-numbers)
     */
    val packetNumberSpacePool = PacketNumberSpace.Pool()

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
    private val localConnectionIDs by lazy {
        ConnectionIDRecordList(localTransportParameters.active_connection_id_limit)
    }

    /**
     * Pool of CIDs, which the peer is willing to accept
     */
    private val peerConnectionIDs by lazy {
        ConnectionIDRecordList(peerTransportParameters.active_connection_id_limit)
    }

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
        println("[Connection] Init connection from ${initialPeerConnectionID.value.toDebugString()}")
    }

    /**
     * @return true if this connection has active [destinationConnectionID] in its pool
     */
    fun match(destinationConnectionID: ConnectionID): Boolean {
        // todo invalidate original dcid?
        return destinationConnectionID.eq(originalDestinationConnectionID) ||
            localConnectionIDs[destinationConnectionID] != null
    }

    suspend fun processPacket(packet: QUICPacket) {
        println("[Connection] Decrypted packet overview:")
        println(packet.toDebugString(withPayload = false).prependIndent("\t"))

        packet.encryptionLevel?.let { level ->
            packetNumberSpacePool[level].receivedPacket(packet.packetNumber)
        }

        val payload = packet.payload ?: return // todo

        while (payload.isNotEmpty) {
            FrameReader.readFrame(processor, packet, peerTransportParameters, maxCIDLength) { error, frame ->
                handleError(error, frame)
                return
            }
        }
    }

    private fun handleError(raisedError: QUICTransportError, frameType: FrameType_v1? = null) {
        error("Error occurred: ${raisedError.toDebugString()}${frameType?.let { "in frame $it" } ?: ""}")
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

    private val outgoingDatagramHandler = OutgoingDatagramHandler(
        outgoingChannel = outgoingDatagramChannel,
        getAddress = { peerSocketAddress },
    )

    private val readyPacketHandler = ReadyPacketHandlerImpl(outgoingDatagramHandler)

    private val initialPacketHandler = PacketSendHandler.Initial(tlsComponent, readyPacketHandler)
    private val handshakePacketHandler = PacketSendHandler.Handshake(tlsComponent, readyPacketHandler)
    private val oneRTTPacketHandler = PacketSendHandler.OneRTT(tlsComponent, readyPacketHandler)

    private suspend fun sendInInitialPacket(
        forceEndPacket: Boolean = false,
        forceEndDatagram: Boolean = false,
        write: FrameWriteFunction,
    ) = send(initialPacketHandler, forceEndPacket, forceEndDatagram, write)

    private suspend fun sendInHandshakePacket(
        forceEndPacket: Boolean = false,
        forceEndDatagram: Boolean = false,
        write: FrameWriteFunction,
    ) = send(handshakePacketHandler, forceEndPacket, forceEndDatagram, write)

    private suspend fun sendInOneRTT(
        forceEndPacket: Boolean = false,
        forceEndDatagram: Boolean = false,
        write: FrameWriteFunction,
    ) = send(oneRTTPacketHandler, forceEndPacket, forceEndDatagram, write)

    private suspend fun send(
        encryptionLevel: EncryptionLevel,
        forceEndPacket: Boolean = false,
        forceEndDatagram: Boolean = false,
        write: FrameWriteFunction,
    ) {
        val handler = when (encryptionLevel) {
            EncryptionLevel.Initial -> initialPacketHandler
            EncryptionLevel.Handshake -> handshakePacketHandler
            EncryptionLevel.AppData -> oneRTTPacketHandler
        }
        send(handler, forceEndPacket, forceEndDatagram, write)
    }

    private suspend fun send(
        packetSendHandler: PacketSendHandler,
        forceEndPacket: Boolean = false,
        forceEndDatagram: Boolean = false,
        write: FrameWriteFunction,
    ) {
        packetSendHandler.writeFrame(write)
        if (forceEndPacket || forceEndDatagram) {
            packetSendHandler.finish()
        }
        if (forceEndDatagram) {
            outgoingDatagramHandler.flush()
        }
    }

    /**
     * Used inside [tlsComponent] to expose necessary QUIC functions to TLS
     */
    private inner class ProtocolCommunicationProviderImpl : ProtocolCommunicationProvider {
        private var offset = 0L

        override val messageChannel: Channel<TLSMessage> = Channel(Channel.UNLIMITED)

        init {
            CoroutineScope(Dispatchers.Default).launch {
                while (isActive) {
                    val result = messageChannel.receive()
                    sendCryptoFrame(result.message, result.isHandshakeMessage, result.flush)
                }
            }
        }

        private suspend fun sendCryptoFrame(cryptoPayload: ByteArray, inHandshakePacket: Boolean, flush: Boolean) {
            println("[CommunicationProvider] offset: $offset, payload.size: ${cryptoPayload.size}, flush: $flush, handshake: $inHandshakePacket") // ktlint-disable max-line-length

            when {
                inHandshakePacket -> {
                    sendInHandshakePacket(
                        forceEndDatagram = flush,
                    ) { builder, _ ->
                        writeCrypto(
                            packetBuilder = builder,
                            offset = offset,
                            data = cryptoPayload,
                        )
                    }
                }

                else -> {
                    sendInInitialPacket(
                        forceEndPacket = flush,
                    ) { builder, hookConsumer ->
                        writeCrypto(
                            packetBuilder = builder,
                            offset = offset,
                            data = cryptoPayload,
                        )

                        withAckFrameIfAny(builder, hookConsumer, EncryptionLevel.Initial)
                    }
                }
            }

            offset = if (flush) 0 else offset + cryptoPayload.size.toLong()
        }

        override suspend fun raiseError(error: QUICTransportError) {
            handleError(error)
        }

        override fun getTransportParameters(peerParameters: TransportParameters): TransportParameters {
            peerTransportParameters = peerParameters

            return transportParameters {
                original_destination_connection_id = originalDestinationConnectionID
                disable_active_migration = true
                active_connection_id_limit = 1
                initial_source_connection_id = initialLocalConnectionID
            }.also {
                localTransportParameters = it

                println("[Connection] Transport parameters are known")
                onTransportParametersKnown()
            }
        }
    }

    private inner class ReadyPacketHandlerImpl(
        private val outgoingDatagramHandler: OutgoingDatagramHandler,
    ) : ReadyPacketHandler.VersionNegotiation,
        ReadyPacketHandler.Initial,
        ReadyPacketHandler.Retry,
        ReadyPacketHandler.OneRTT {

        override val destinationConnectionID: ConnectionID
            get() = peerConnectionIDs.nextConnectionID()

        override val sourceConnectionID: ConnectionID
            get() = localConnectionIDs.nextConnectionID()

        // These sizes are static during connection
        override val destinationConnectionIDSize: Int by lazy { destinationConnectionID.size }

        override val sourceConnectionIDSize: Int by lazy { sourceConnectionID.size }

        override suspend fun withDatagramBuilder(body: suspend (BytePacketBuilder) -> Unit) {
            outgoingDatagramHandler.write(body)
        }

        override fun getPacketNumber(encryptionLevel: EncryptionLevel): Long {
            return packetNumberSpacePool[encryptionLevel].next()
        }

        override fun getLargestAcknowledged(encryptionLevel: EncryptionLevel): Long {
            return packetNumberSpacePool[encryptionLevel].largestAcknowledged
        }

        override val usedDatagramSize: Int
            get() = outgoingDatagramHandler.usedSize

        override val maxUdpPayloadSize: Int
            get() = peerTransportParameters.max_udp_payload_size

        override suspend fun forceEndDatagram() {
            outgoingDatagramHandler.flush()
        }

        // Initial packet
        /**
         * Initial packets sent by the server MUST set the Token Length field to 0.
         *
         * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-initial-packet)
         */
        override val token: ByteArray = EMPTY_BYTE_ARRAY

        // Retry packet
        override val originalDestinationConnectionID: ConnectionID
            get() = TODO("Not yet implemented")
        override val retryToken: ByteArray
            get() = TODO("Not yet implemented")

        // 1-RTT packet
        override val spinBit: Boolean
            get() = TODO("Not yet implemented")
        override val keyPhase: Boolean
            get() = TODO("Not yet implemented")

        // Version Negotiation packet
        override val supportedVersions: Array<UInt32> = arrayOf(QUICVersion.V1)
    }

    private inner class PayloadProcessor : FrameProcessor {
        override suspend fun acceptPadding(packet: QUICPacket): QUICTransportError_v1? {
//            logAcceptedFrame(FrameType_v1.PADDING)
            return null
        }

        override suspend fun acceptPing(packet: QUICPacket): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.PING)
            // The receiver of a PING frame simply needs to acknowledge the packet containing this frame.

            val encryptionLevel = packet.encryptionLevel ?: return TransportError_v1.PROTOCOL_VIOLATION

            // todo remove force after adding packet auto sending loop
            send(encryptionLevel, forceEndDatagram = true) { builder, hookConsumer ->
                withAckFrameIfAny(builder, hookConsumer, encryptionLevel)
            }

            return null
        }

        override suspend fun acceptACK(
            packet: QUICPacket,
            ackDelay: Long,
            ackRanges: LongArray,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.ACK)

            packet.encryptionLevel?.let { level ->
                packetNumberSpacePool[level].processAcknowledgements(ackRanges)
            }

            return null
        }

        override suspend fun acceptACKWithECN(
            packet: QUICPacket,
            ackDelay: Long,
            ackRanges: LongArray,
            ect0: Long,
            ect1: Long,
            ectCE: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.ACK_ECN)

            packet.encryptionLevel?.let { level ->
                packetNumberSpacePool[level].processAcknowledgements(ackRanges)
            }

            return null
        }

        override suspend fun acceptResetStream(
            packet: QUICPacket,
            streamId: Long,
            applicationProtocolErrorCode: AppError,
            finalSize: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.RESET_STREAM)
            return null
        }

        override suspend fun acceptStopSending(
            packet: QUICPacket,
            streamId: Long,
            applicationProtocolErrorCode: AppError,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.STOP_SENDING)
            return null
        }

        override suspend fun acceptCrypto(
            packet: QUICPacket,
            offset: Long,
            cryptoData: ByteArray,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.CRYPTO)

            when (packet.encryptionLevel) {
                EncryptionLevel.Initial -> tlsComponent.acceptInitialHandshake(cryptoData)
                EncryptionLevel.Handshake -> {
                    tlsComponent.finishHandshake(cryptoData)

                    sendInHandshakePacket(forceEndPacket = true) { builder, hookConsumer ->
                        withAckFrameIfAny(builder, hookConsumer, EncryptionLevel.Handshake)
                    }

                    sendInOneRTT { builder, _ ->
                        writeHandshakeDone(builder)
                    }
                }

                EncryptionLevel.AppData -> TODO("Not implemented yet")
                else -> return TransportError_v1.PROTOCOL_VIOLATION
            }

            return null
        }

        override suspend fun acceptNewToken(packet: QUICPacket, token: ByteArray): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.NEW_TOKEN)
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
            logAcceptedFrame(FrameType_v1.STREAM)
            return null
        }

        override suspend fun acceptMaxData(packet: QUICPacket, maximumData: Long): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.MAX_DATA)
            peerMaxData = maximumData.coerceAtLeast(peerMaxData)

            return null
        }

        override suspend fun acceptMaxStreamData(
            packet: QUICPacket,
            streamId: Long,
            maximumStreamData: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.MAX_STREAM_DATA)
            maxStreamData[streamId] = maximumStreamData.coerceAtLeast(maxStreamData[streamId] ?: 0)

            // todo check receive-only and not created streams

            return null
        }

        override suspend fun acceptMaxStreamsBidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL)
            peerMaxStreamsBidirectional = maximumStreams.coerceAtLeast(peerMaxStreamsBidirectional)

            return null
        }

        override suspend fun acceptMaxStreamsUnidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL)
            peerMaxStreamsUnidirectional = maximumStreams.coerceAtLeast(peerMaxStreamsUnidirectional)

            return null
        }

        override suspend fun acceptDataBlocked(packet: QUICPacket, maximumData: Long): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.DATA_BLOCKED)
            return null
        }

        override suspend fun acceptStreamDataBlocked(
            packet: QUICPacket,
            streamId: Long,
            maximumStreamData: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.STREAM_DATA_BLOCKED)
            return null
        }

        override suspend fun acceptStreamsBlockedBidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL)
            return null
        }

        override suspend fun acceptStreamsBlockedUnidirectional(
            packet: QUICPacket,
            maximumStreams: Long,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL)
            return null
        }

        override suspend fun acceptNewConnectionId(
            packet: QUICPacket,
            sequenceNumber: Long,
            retirePriorTo: Long,
            connectionID: ConnectionID,
            statelessResetToken: ByteArray?,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.NEW_CONNECTION_ID)
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
                sendInOneRTT { builder, _ ->
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
            sendInOneRTT { builder, _ ->
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
            logAcceptedFrame(FrameType_v1.RETIRE_CONNECTION_ID)
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
            logAcceptedFrame(FrameType_v1.PATH_CHALLENGE)
            sendInOneRTT { builder, _ ->
                writePathResponse(builder, data)
            }

            return null
        }

        override suspend fun acceptPathResponse(packet: QUICPacket, data: ByteArray): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.PATH_RESPONSE)
            // todo check data is eq to PATH_CHALLENGE

            return null
        }

        override suspend fun acceptConnectionCloseWithTransportError(
            packet: QUICPacket,
            errorCode: QUICTransportError_v1,
            frameType: FrameType_v1,
            reasonPhrase: ByteArray,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR)
            return null
        }

        override suspend fun acceptConnectionCloseWithAppError(
            packet: QUICPacket,
            errorCode: AppError,
            reasonPhrase: ByteArray,
        ): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.CONNECTION_CLOSE_APP_ERR)
            return null
        }

        override suspend fun acceptHandshakeDone(packet: QUICPacket): QUICTransportError_v1? {
            logAcceptedFrame(FrameType_v1.HANDSHAKE_DONE)
            if (isServer) {
                return TransportError_v1.PROTOCOL_VIOLATION
            }

            return null
        }

        private fun logAcceptedFrame(frameType: FrameType_v1) {
            println("[FrameProcessor] Accepted frame: $frameType")
        }
    }

    private fun FrameWriter.withAckFrameIfAny(
        builder: BytePacketBuilder,
        hookConsumer: ((Long) -> Unit) -> Unit,
        encryptionLevel: EncryptionLevel,
    ) {
        packetNumberSpacePool[encryptionLevel].getAckRanges()?.let { (ranges, hook) ->
            hookConsumer(hook)

            writeACK(
                packetBuilder = builder,
                ackDelay = 0,
                ack_delay_exponent = localTransportParameters.ack_delay_exponent,
                ackRanges = ranges,
            )
        }
    }
}
