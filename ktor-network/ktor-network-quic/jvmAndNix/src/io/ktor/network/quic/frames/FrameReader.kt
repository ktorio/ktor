/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.errors.TransportError_v1.*
import io.ktor.network.quic.packets.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

internal object FrameReader {
    suspend inline fun readFrame(
        processor: FrameProcessor,
        payload: ByteReadPacket,
        packetTransportParameters: PacketTransportParameters,
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
            FrameType_v1.PADDING -> processor.acceptPadding()
            FrameType_v1.PING -> processor.acceptPing()

            FrameType_v1.ACK -> readAndProcessACK(
                processor = processor,
                payload = payload,
                ack_delay_exponent = packetTransportParameters.ack_delay_exponent,
                withECN = false
            )

            FrameType_v1.ACK_ECN -> readAndProcessACK(
                processor = processor,
                payload = payload,
                ack_delay_exponent = packetTransportParameters.ack_delay_exponent,
                withECN = true
            )

            FrameType_v1.RESET_STREAM -> readAndProcessResetStream(processor, payload)
            FrameType_v1.STOP_SENDING -> readAndProcessStopSending(processor, payload)
            FrameType_v1.CRYPTO -> readAndProcessCrypto(processor, payload)
            FrameType_v1.NEW_TOKEN -> readAndProcessNewToken(processor, payload)

            FrameType_v1.STREAM,
            FrameType_v1.STREAM_FIN,
            FrameType_v1.STREAM_LEN,
            FrameType_v1.STREAM_LEN_FIN,
            FrameType_v1.STREAM_OFF,
            FrameType_v1.STREAM_OFF_FIN,
            FrameType_v1.STREAM_OFF_LEN,
            FrameType_v1.STREAM_OFF_LEN_FIN,
            -> readAndProcessStream(processor, payload, type)

            FrameType_v1.MAX_DATA -> readAndProcessMaxData(processor, payload)
            FrameType_v1.MAX_STREAM_DATA -> readAndProcessMaxStreamData(processor, payload)
            FrameType_v1.MAX_STREAMS_BIDIRECTIONAL -> readAndProcessMaxStreamsBidirectional(processor, payload)
            FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL -> readAndProcessMaxStreamUnidirectional(processor, payload)
            FrameType_v1.DATA_BLOCKED -> readAndProcessDataBlocked(processor, payload)
            FrameType_v1.STREAM_DATA_BLOCKED -> readAndProcessStreamDataBlocked(processor, payload)
            FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL -> readAndProcessStreamsBlockedBidirectional(processor, payload)
            FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL -> readAndProcessStreamsBlockedUnidirectional(
                processor,
                payload
            )

            FrameType_v1.NEW_CONNECTION_ID -> readAndProcessNewConnectionId(processor, payload)
            FrameType_v1.RETIRE_CONNECTION_ID -> readAndProcessRetireConnectionId(processor, payload)
            FrameType_v1.PATH_CHALLENGE -> readAndProcessPathChallenge(processor, payload)
            FrameType_v1.PATH_RESPONSE -> readAndProcessPathResponse(processor, payload)
            FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR -> readAndProcessConnectionCloseWithTransportError(
                processor,
                payload
            )

            FrameType_v1.CONNECTION_CLOSE_APP_ERR -> readAndProcessConnectionCloseWithAppError(processor, payload)
            FrameType_v1.HANDSHAKE_DONE -> processor.acceptHandshakeDone()
        }

        err?.let { onError(it, type) }
    }

    @Suppress("LocalVariableName")
    private suspend fun readAndProcessACK(
        processor: FrameProcessor,
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
                    ackDelay = ackDelay,
                    ackRanges = ackRanges,
                    ect0 = ect0,
                    ect1 = ect1,
                    ectCE = ectCE
                )
            }

            else -> processor.acceptACK(
                ackDelay = ackDelay,
                ackRanges = ackRanges,
            )
        }
    }

    private suspend fun readAndProcessResetStream(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val applicationProtocolErrorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }
        val finalSize = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptResetStream(streamId, applicationProtocolErrorCode, finalSize)
    }

    private suspend fun readAndProcessStopSending(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val applicationProtocolErrorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptStopSending(streamId, applicationProtocolErrorCode)
    }

    private suspend fun readAndProcessCrypto(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val offset = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val length = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (offset + length >= POW_2_62 || payload.remaining < length) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val data = payload.readBytes(length.toInt()) // todo remove allocation?

        return processor.acceptCrypto(offset, data)
    }

    private suspend fun readAndProcessNewToken(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val tokenLength = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (tokenLength == 0L || payload.remaining < tokenLength) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val token = payload.readBytes(tokenLength.toInt()) // todo remove allocation?

        return processor.acceptNewToken(token)
    }

    private suspend fun readAndProcessStream(
        processor: FrameProcessor,
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

        return processor.acceptStream(streamId, offset, fin, streamData)
    }

    private suspend fun readAndProcessMaxData(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptMaxData(maximumData)
    }

    private suspend fun readAndProcessMaxStreamData(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val maximumStreamData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptMaxStreamData(streamId, maximumStreamData)
    }

    private suspend fun readAndProcessMaxStreamsBidirectional(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptMaxStreamsBidirectional(maximumStreams)
    }

    private suspend fun readAndProcessMaxStreamUnidirectional(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptMaxStreamsUnidirectional(maximumStreams)
    }

    private suspend fun readAndProcessDataBlocked(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptDataBlocked(maximumData)
    }

    private suspend fun readAndProcessStreamDataBlocked(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val streamId = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val maximumStreamData = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptStreamDataBlocked(streamId, maximumStreamData)
    }

    private suspend fun readAndProcessStreamsBlockedBidirectional(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptStreamsBlockedBidirectional(maximumStreams)
    }

    private suspend fun readAndProcessStreamsBlockedUnidirectional(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val maximumStreams = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (maximumStreams > POW_2_60) {
            return FRAME_ENCODING_ERROR
        }

        return processor.acceptStreamsBlockedUnidirectional(maximumStreams)
    }

    @OptIn(ExperimentalUnsignedTypes::class)
    private suspend fun readAndProcessNewConnectionId(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val sequenceNumber = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }
        val retirePriorTo = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (retirePriorTo > sequenceNumber) {
            return FRAME_ENCODING_ERROR
        }

        val length = payload.readUByteOrElse { return FRAME_ENCODING_ERROR }.toInt()

        if (length !in 1..20 || payload.remaining < length) {
            return FRAME_ENCODING_ERROR
        }

        val connectionId = payload.readBytes(length) // todo remove allocation?

        if (payload.remaining < 16) {
            return FRAME_ENCODING_ERROR
        }

        val statelessResetToken = payload.readBytes(16) // todo remove allocation?

        return processor.acceptNewConnectionId(sequenceNumber, retirePriorTo, connectionId, statelessResetToken)
    }

    private suspend fun readAndProcessRetireConnectionId(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val sequenceNumber = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        return processor.acceptRetireConnectionId(sequenceNumber)
    }

    private suspend fun readAndProcessPathChallenge(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        if (payload.remaining < 8) {
            return FRAME_ENCODING_ERROR
        }

        val data = payload.readBytes(8)

        return processor.acceptPathChallenge(data)
    }

    private suspend fun readAndProcessPathResponse(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        if (payload.remaining < 8) {
            return FRAME_ENCODING_ERROR
        }

        val data = payload.readBytes(8)

        return processor.acceptPathResponse(data)
    }

    private suspend fun readAndProcessConnectionCloseWithTransportError(
        processor: FrameProcessor,
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

        return processor.acceptConnectionCloseWithTransportError(errorCode, frameType, reasonPhrase)
    }

    private suspend fun readAndProcessConnectionCloseWithAppError(
        processor: FrameProcessor,
        payload: ByteReadPacket,
    ): QUICTransportError_v1? {
        val errorCode = payload.readAppErrorOrElse { return FRAME_ENCODING_ERROR }
        val reasonPhraseLength = payload.readVarIntOrElse { return FRAME_ENCODING_ERROR }

        if (payload.remaining < reasonPhraseLength) {
            return FRAME_ENCODING_ERROR
        }

        // conversion to int should be ok here, as we are restricted by common sense (datagram size)
        val reasonPhrase = payload.readBytes(reasonPhraseLength.toInt()) // todo remove allocation ?

        return processor.acceptConnectionCloseWithAppError(errorCode, reasonPhrase)
    }

    // HELPER FUNCTIONS AND VALUES

    private inline fun ByteReadPacket.readAppErrorOrElse(elseBlock: () -> Nothing): AppError {
        return AppError(readVarIntOrElse(elseBlock))
    }

    private fun ByteReadPacket.readTransportError(): QUICTransportError_v1? {
        return QUICTransportError_v1.readFromFrame(this)
    }

    @Suppress("NOTHING_TO_INLINE")
    @OptIn(ExperimentalUnsignedTypes::class)
    private inline fun ByteReadPacket.readFrameType(): FrameType_v1? {
        return FrameType_v1.fromByte(readUByteOrElse { return null })
    }
}
