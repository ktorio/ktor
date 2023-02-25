/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.errors.TransportError_v1.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

internal object FrameReader {
    suspend inline fun readFrame(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
        transportParameters: TransportParameters,
        maxCIDLength: UInt8,
        onError: (QUICTransportError_v1, FrameType_v1) -> Unit,
    ) {
        if (payload.isEmpty) {
            onError(PROTOCOL_VIOLATION, FrameType_v1.PADDING)
            return
        }

        val type = payload.readFrameType()

        if (type == null) {
            onError(FRAME_ENCODING_ERROR, FrameType_v1.PADDING)
            return
        }

        val err = when (type) {
            FrameType_v1.PADDING -> processor.acceptPadding(packet)
            FrameType_v1.PING -> processor.acceptPing(packet)

            FrameType_v1.ACK -> readAndProcessACK(
                processor = processor,
                packet = packet,
                payload = payload,
                ack_delay_exponent = transportParameters.ack_delay_exponent,
                withECN = false
            )

            FrameType_v1.ACK_ECN -> readAndProcessACK(
                processor = processor,
                packet = packet,
                payload = payload,
                ack_delay_exponent = transportParameters.ack_delay_exponent,
                withECN = true
            )

            FrameType_v1.RESET_STREAM -> readAndProcessResetStream(processor, packet, payload)
            FrameType_v1.STOP_SENDING -> readAndProcessStopSending(processor, packet, payload)
            FrameType_v1.CRYPTO -> readAndProcessCrypto(processor, packet, payload)
            FrameType_v1.NEW_TOKEN -> readAndProcessNewToken(processor, packet, payload)

            FrameType_v1.STREAM,
            FrameType_v1.STREAM_FIN,
            FrameType_v1.STREAM_LEN,
            FrameType_v1.STREAM_LEN_FIN,
            FrameType_v1.STREAM_OFF,
            FrameType_v1.STREAM_OFF_FIN,
            FrameType_v1.STREAM_OFF_LEN,
            FrameType_v1.STREAM_OFF_LEN_FIN,
            -> readAndProcessStream(processor, packet, payload, type)

            FrameType_v1.MAX_DATA -> readAndProcessMaxData(processor, packet, payload)
            FrameType_v1.MAX_STREAM_DATA -> readAndProcessMaxStreamData(processor, packet, payload)
            FrameType_v1.MAX_STREAMS_BIDIRECTIONAL -> readAndProcessMaxStreamsBidirectional(processor, packet, payload)
            FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL -> readAndProcessMaxStreamUnidirectional(processor, packet, payload)
            FrameType_v1.DATA_BLOCKED -> readAndProcessDataBlocked(processor, packet, payload)
            FrameType_v1.STREAM_DATA_BLOCKED -> readAndProcessStreamDataBlocked(processor, packet, payload)
            FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL -> readAndProcessStreamsBlockedBidirectional(processor, packet, payload)
            FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL -> readAndProcessStreamsBlockedUnidirectional(
                processor,
                packet,
                payload
            )

            FrameType_v1.NEW_CONNECTION_ID -> readAndProcessNewConnectionId(processor, packet, maxCIDLength, payload)
            FrameType_v1.RETIRE_CONNECTION_ID -> readAndProcessRetireConnectionId(processor, packet, payload)
            FrameType_v1.PATH_CHALLENGE -> readAndProcessPathChallenge(processor, packet, payload)
            FrameType_v1.PATH_RESPONSE -> readAndProcessPathResponse(processor, packet, payload)
            FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR -> readAndProcessConnectionCloseWithTransportError(
                processor,
                packet,
                payload
            )

            FrameType_v1.CONNECTION_CLOSE_APP_ERR -> readAndProcessConnectionCloseWithAppError(processor, packet, payload)
            FrameType_v1.HANDSHAKE_DONE -> processor.acceptHandshakeDone(packet)
        }

        err?.let { onError(it, type) }
    }

    @Suppress("LocalVariableName")
    private suspend fun readAndProcessACK(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
        ack_delay_exponent: Int,
        withECN: Boolean,
    ): QUICTransportError_v1? {
        val largestAcknowledged = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val ackDelay = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR } shl ack_delay_exponent

        val ackRangeCount = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val firstAckRange = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        val ackRanges = LongArray((ackRangeCount * 2 + 2).toInt())
        ackRanges[0] = largestAcknowledged
        var previousSmallest = largestAcknowledged - firstAckRange
        ackRanges[1] = previousSmallest

        var i = 2
        while (i < ackRanges.size) {
            val gap = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
            val largest = previousSmallest - gap - 2

            if (largest < 0) {
                return FRAME_ENCODING_ERROR
            }

            val length = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
            ackRanges[i] = largest
            previousSmallest = largest - length

            if (previousSmallest < 0) {
                return FRAME_ENCODING_ERROR
            }

            ackRanges[i + 1] = previousSmallest

            i += 2
        }

        return when {
            withECN -> {
                val ect0 = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
                val ect1 = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
                val ectCE = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

                processor.acceptACKWithECN(
                    packet = packet,
                    ackDelay = ackDelay,
                    ackRanges = ackRanges,
                    ect0 = ect0,
                    ect1 = ect1,
                    ectCE = ectCE
                )
            }

            else -> processor.acceptACK(
                packet = packet,
                ackDelay = ackDelay,
                ackRanges = ackRanges,
            )
        }
    }

    private suspend fun readAndProcessResetStream(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val applicationProtocolErrorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }
        val finalSize = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptResetStream(packet, streamId, applicationProtocolErrorCode, finalSize)
    }

    private suspend fun readAndProcessStopSending(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val applicationProtocolErrorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptStopSending(packet, streamId, applicationProtocolErrorCode)
    }

    private suspend fun readAndProcessCrypto(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val offset = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val length = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (offset + length >= POW_2_62 || payload.remaining < length) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val data = payload.readBytes(length.toInt()) // todo remove allocation?

        return processor.acceptCrypto(packet, offset, data)
    }

    private suspend fun readAndProcessNewToken(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val tokenLength = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (tokenLength == 0L || payload.remaining < tokenLength) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val token = payload.readBytes(tokenLength.toInt()) // todo remove allocation?

        return processor.acceptNewToken(packet, token)
    }

    private suspend fun readAndProcessStream(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
        type: FrameType_v1,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        val bits = type.typeValue.toInt()
        val off = ((bits ushr 2) and 1) == 1
        val len = ((bits ushr 1) and 1) == 1
        val fin = (bits and 1) == 1

        val offset = if (!off) 0L else (payload.readVarIntOrElse { return FRAME_ENCODING_ERROR })
        val length = if (!len) null else (payload.readVarIntOrElse { return FRAME_ENCODING_ERROR })

        if (length != null && offset + length >= POW_2_62) {
            return FRAME_ENCODING_ERROR
        }

        val streamData = when {
            length == null -> payload.readBytes()

            // conversion to int should be ok here, as we are restricted by common sense (datagram size)
            payload.remaining >= length -> payload.readBytes(length.toInt())

            else -> return FRAME_ENCODING_ERROR
        }

        if (length == null && streamData.size + offset >= POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptStream(packet, streamId, offset, fin, streamData)
    }

    private suspend fun readAndProcessMaxData(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptMaxData(packet, maximumData)
    }

    private suspend fun readAndProcessMaxStreamData(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val maximumStreamData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptMaxStreamData(packet, streamId, maximumStreamData)
    }

    private suspend fun readAndProcessMaxStreamsBidirectional(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptMaxStreamsBidirectional(packet, maximumStreams)
    }

    private suspend fun readAndProcessMaxStreamUnidirectional(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptMaxStreamsUnidirectional(packet, maximumStreams)
    }

    private suspend fun readAndProcessDataBlocked(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptDataBlocked(packet, maximumData)
    }

    private suspend fun readAndProcessStreamDataBlocked(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val maximumStreamData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptStreamDataBlocked(packet, streamId, maximumStreamData)
    }

    private suspend fun readAndProcessStreamsBlockedBidirectional(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptStreamsBlockedBidirectional(packet, maximumStreams)
    }

    private suspend fun readAndProcessStreamsBlockedUnidirectional(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptStreamsBlockedUnidirectional(packet, maximumStreams)
    }

    private suspend fun readAndProcessNewConnectionId(
        processor: FrameProcessor,
        packet: QUICPacket,
        maxCIDLength: UInt8,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val sequenceNumber: Long = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val retirePriorTo: Long = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (retirePriorTo > sequenceNumber) {
            return FRAME_ENCODING_ERROR
        }

        val length: Int = payload.readUInt8 { return FRAME_ENCODING_ERROR }.toInt()

        if (length < 1 || length > maxCIDLength.toInt() || payload.remaining < length) {
            return FRAME_ENCODING_ERROR
        }

        val connectionID: ConnectionID = payload.readBytes(length) // todo remove allocation?

        if (payload.remaining < 16) {
            return FRAME_ENCODING_ERROR
        }

        val statelessResetToken = payload.readBytes(16) // todo remove allocation?

        return processor.acceptNewConnectionId(packet, sequenceNumber, retirePriorTo, connectionID, statelessResetToken)
    }

    private suspend fun readAndProcessRetireConnectionId(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val sequenceNumber = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptRetireConnectionId(packet, sequenceNumber)
    }

    private suspend fun readAndProcessPathChallenge(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        if (payload.remaining < 8) {
            return FRAME_ENCODING_ERROR
        }

        val data = payload.readBytes(8)

        return processor.acceptPathChallenge(packet, data)
    }

    private suspend fun readAndProcessPathResponse(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        if (payload.remaining < 8) {
            return FRAME_ENCODING_ERROR
        }

        val data = payload.readBytes(8)

        return processor.acceptPathResponse(packet, data)
    }

    private suspend fun readAndProcessConnectionCloseWithTransportError(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val errorCode = payload.readTransportError() ?: return FRAME_ENCODING_ERROR
        val frameType = payload.readFrameType() ?: return FRAME_ENCODING_ERROR
        val reasonPhraseLength = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (payload.remaining < reasonPhraseLength) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val reasonPhrase = payload.readBytes(reasonPhraseLength.toInt()) // todo remove allocation ?

        return processor.acceptConnectionCloseWithTransportError(packet, errorCode, frameType, reasonPhrase)
    }

    private suspend fun readAndProcessConnectionCloseWithAppError(
        processor: FrameProcessor,
        packet: QUICPacket,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val errorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }
        val reasonPhraseLength = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (payload.remaining < reasonPhraseLength) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val reasonPhrase = payload.readBytes(reasonPhraseLength.toInt()) // todo remove allocation ?

        return processor.acceptConnectionCloseWithAppError(packet, errorCode, reasonPhrase)
    }

    // HELPER FUNCTIONS AND VALUES

    private inline fun ByteReadPacket.readAppErrorOrElse(elseBlock: () -> Nothing): AppError {
        return AppError(readVarIntOrElse(elseBlock))
    }

    private fun ByteReadPacket.readTransportError(): QUICTransportError_v1? {
        return QUICTransportError_v1.readFromFrame(this)
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun ByteReadPacket.readFrameType(): FrameType_v1? {
        return FrameType_v1.fromByte(readUInt8 { return null })
    }
}
