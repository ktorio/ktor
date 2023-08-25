/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("LocalVariableName")

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*

/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-frame-types-and-formats)
 */
internal interface FrameWriter {
    suspend fun writePadding(): Long

    suspend fun writePing(): Long

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is decomposed into **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun writeACK(
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
    ): Long

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is decomposed into **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun writeACKWithECN(
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): Long

    suspend fun writeResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ): Long

    suspend fun writeStopSending(streamId: Long, applicationProtocolErrorCode: AppError): Long

    suspend fun writeCrypto(offset: Long, data: ByteArray): Long

    suspend fun writeNewToken(token: ByteArray): Long

    suspend fun writeStream(
        streamId: Long,
        offset: Long?,
        specifyLength: Boolean,
        fin: Boolean,
        data: ByteArray,
    ): Long

    suspend fun writeMaxData(maximumData: Long): Long

    suspend fun writeMaxStreamData(streamId: Long, maximumStreamData: Long): Long

    suspend fun writeMaxStreamsBidirectional(maximumStreams: Long): Long

    suspend fun writeMaxStreamsUnidirectional(maximumStreams: Long): Long

    suspend fun writeDataBlocked(maximumData: Long): Long

    suspend fun writeStreamDataBlocked(streamId: Long, maximumStreamData: Long): Long

    suspend fun writeStreamsBlockedBidirectional(maximumStreams: Long): Long

    suspend fun writeStreamsBlockedUnidirectional(maximumStreams: Long): Long

    suspend fun writeNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray,
    ): Long

    suspend fun writeRetireConnectionId(sequenceNumber: Long): Long

    suspend fun writePathChallenge(data: ByteArray): Long

    suspend fun writePathResponse(data: ByteArray): Long

    suspend fun writeConnectionCloseWithTransportError(
        errorCode: QUICTransportError,
        frameTypeV1: QUICFrameType?,
        reasonPhrase: ByteArray,
    ): Long

    suspend fun writeConnectionCloseWithAppError(applicationProtocolErrorCode: AppError, reasonPhrase: ByteArray): Long

    suspend fun writeHandshakeDone(): Long
}

internal class FrameWriterImpl(
    packetSendHandler: PacketSendHandler,
) : FrameWriter, PacketSendHandler by packetSendHandler {
    override suspend fun writePadding(): Long = writeFrame(PayloadSize.FRAME_TYPE_SIZE) {
        writeFrameType(QUICFrameType.PADDING)
    }

    override suspend fun writePing(): Long = writeFrame(PayloadSize.FRAME_TYPE_SIZE) {
        writeFrameType(QUICFrameType.PING)
    }

    override suspend fun writeACK(
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
    ): Long = writeACK(
        typeV1 = QUICFrameType.ACK,
        ackDelay = ackDelay,
        ack_delay_exponent = ack_delay_exponent,
        ackRanges = ackRanges,
        ect0 = null,
        ect1 = null,
        ectCE = null
    )

    override suspend fun writeACKWithECN(
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): Long = writeACK(
        typeV1 = QUICFrameType.ACK_ECN,
        ackDelay = ackDelay,
        ack_delay_exponent = ack_delay_exponent,
        ackRanges = ackRanges,
        ect0 = ect0,
        ect1 = ect1,
        ectCE = ectCE
    )

    private suspend fun writeACK(
        typeV1: QUICFrameType,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
        ect0: Long?,
        ect1: Long?,
        ectCE: Long?,
    ): Long {
        require(ackRanges.isNotEmpty()) {
            "'ackRanges' parameter can not be empty"
        }
        require(ackRanges.size % 2 == 0) {
            "'ackRanges' parameter's size should be even"
        }

        val largestAcknowledged: Long = ackRanges[0]
        val firstACKRange: Long = largestAcknowledged - ackRanges[1]
        val ackRangeCount: Int = ackRanges.size / 2 - 1
        val ackDelayPowered: Long = ackDelay ushr ack_delay_exponent

        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(largestAcknowledged) +
                PayloadSize.ofVarInt(ackDelayPowered) +
                PayloadSize.ofVarInt(ackRangeCount) +
                PayloadSize.ofVarInt(firstACKRange)

        val expectedRangesSize: Int = ackRangeCount * 2 * PayloadSize.LONG_SIZE

        val expectedEctSize: Int = ect0?.let {
            PayloadSize.ofVarInt(ect0) +
                PayloadSize.ofVarInt(ect1!!) +
                PayloadSize.ofVarInt(ectCE!!)
        } ?: 0

        return writeFrame(expectedSize + expectedRangesSize + expectedEctSize) {
            writeFrameType(typeV1)
            writeVarInt(largestAcknowledged)
            writeVarInt(ackDelayPowered)
            writeVarInt(ackRangeCount)
            writeVarInt(firstACKRange)

            var lastSmallestAcknowledged = ackRanges[1]
            var i = 2
            while (i < ackRanges.size) {
                val gap = lastSmallestAcknowledged - ackRanges[i] - 2
                lastSmallestAcknowledged = ackRanges[i + 1]
                val length = ackRanges[i] - lastSmallestAcknowledged

                writeVarInt(gap)
                writeVarInt(length)

                i += 2
            }

            ect0?.let {
                writeVarInt(ect0)
                writeVarInt(ect1!!)
                writeVarInt(ectCE!!)
            }
        }
    }

    override suspend fun writeResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(streamId) +
                PayloadSize.ofVarInt(applicationProtocolErrorCode.intCode) +
                PayloadSize.ofVarInt(finalSize)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.RESET_STREAM)
            writeVarInt(streamId)
            applicationProtocolErrorCode.writeToFrame(this)
            writeVarInt(finalSize)
        }
    }

    override suspend fun writeStopSending(streamId: Long, applicationProtocolErrorCode: AppError): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(streamId) +
                PayloadSize.ofVarInt(applicationProtocolErrorCode.intCode)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.STOP_SENDING)
            writeVarInt(streamId)
            applicationProtocolErrorCode.writeToFrame(this)
        }
    }

    override suspend fun writeCrypto(offset: Long, data: ByteArray): Long {
        require(offset + data.size < POW_2_62) {
            "The sum of the 'offset' and 'data length' cannot exceed (2^62 - 1)"
        }

        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(offset) +
                PayloadSize.ofByteArrayWithLength(data)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.CRYPTO)
            writeVarInt(offset)
            writeVarInt(data.size)
            writeFully(data)
        }
    }

    override suspend fun writeNewToken(token: ByteArray): Long {
        require(token.isNotEmpty()) {
            "The 'token' MUST NOT be empty."
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofByteArrayWithLength(token)) {
            writeFrameType(QUICFrameType.NEW_TOKEN)
            writeVarInt(token.size)
            writeFully(token)
        }
    }

    override suspend fun writeStream(
        streamId: Long,
        offset: Long?,
        specifyLength: Boolean,
        fin: Boolean,
        data: ByteArray,
    ): Long {
        require((offset ?: 0) + data.size.toLong() <= POW_2_62 - 1) {
            "The sum of the 'offset' and 'data length' - MUST NOT exceed 2^62 - 1"
        }

        val length: Long? = if (specifyLength) data.size.toLong() else null

        @Suppress("KotlinConstantConditions")
        val type = when {
            offset == null && length == null && !fin -> QUICFrameType.STREAM
            offset == null && length == null && fin -> QUICFrameType.STREAM_FIN
            offset == null && length != null && !fin -> QUICFrameType.STREAM_LEN
            offset == null && length != null && fin -> QUICFrameType.STREAM_LEN_FIN
            offset != null && length == null && !fin -> QUICFrameType.STREAM_OFF
            offset != null && length == null && fin -> QUICFrameType.STREAM_OFF_FIN
            offset != null && length != null && !fin -> QUICFrameType.STREAM_OFF_LEN
            offset != null && length != null && fin -> QUICFrameType.STREAM_OFF_LEN_FIN
            else -> unreachable()
        }

        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(streamId) +
                (offset?.let { PayloadSize.ofVarInt(offset) } ?: 0) +
                (length?.let { PayloadSize.ofVarInt(length) } ?: 0) +
                PayloadSize.ofByteArray(data)

        return writeFrame(expectedSize) {
            writeFrameType(type)
            writeVarInt(streamId)
            offset?.let { writeVarInt(it) }
            length?.let { writeVarInt(it) }
            writeFully(data)
        }
    }

    override suspend fun writeMaxData(maximumData: Long): Long {
        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumData)) {
            writeFrameType(QUICFrameType.MAX_DATA)
            writeVarInt(maximumData)
        }
    }

    override suspend fun writeMaxStreamData(streamId: Long, maximumStreamData: Long): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(streamId) +
                PayloadSize.ofVarInt(maximumStreamData)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.MAX_STREAM_DATA)
            writeVarInt(streamId)
            writeVarInt(maximumStreamData)
        }
    }

    override suspend fun writeMaxStreamsBidirectional(maximumStreams: Long): Long {
        require(maximumStreams <= POW_2_60) {
            "'Maximum streams' MUST NOT exceed 2^60."
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumStreams)) {
            writeFrameType(QUICFrameType.MAX_STREAMS_BIDIRECTIONAL)
            writeVarInt(maximumStreams)
        }
    }

    override suspend fun writeMaxStreamsUnidirectional(maximumStreams: Long): Long {
        require(maximumStreams <= POW_2_60) {
            "'Maximum streams' MUST NOT exceed 2^60."
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumStreams)) {
            writeFrameType(QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL)
            writeVarInt(maximumStreams)
        }
    }

    override suspend fun writeDataBlocked(maximumData: Long): Long {
        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumData)) {
            writeFrameType(QUICFrameType.DATA_BLOCKED)
            writeVarInt(maximumData)
        }
    }

    override suspend fun writeStreamDataBlocked(streamId: Long, maximumStreamData: Long): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(streamId) +
                PayloadSize.ofVarInt(maximumStreamData)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.STREAM_DATA_BLOCKED)
            writeVarInt(streamId)
            writeVarInt(maximumStreamData)
        }
    }

    override suspend fun writeStreamsBlockedBidirectional(maximumStreams: Long): Long {
        require(maximumStreams <= POW_2_60) {
            "'Maximum streams' MUST NOT exceed 2^60."
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumStreams)) {
            writeFrameType(QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL)
            writeVarInt(maximumStreams)
        }
    }

    override suspend fun writeStreamsBlockedUnidirectional(maximumStreams: Long): Long {
        require(maximumStreams <= POW_2_60) {
            "'Maximum streams' MUST NOT exceed 2^60."
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(maximumStreams)) {
            writeFrameType(QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL)
            writeVarInt(maximumStreams)
        }
    }

    override suspend fun writeNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray,
    ): Long {
        require(retirePriorTo <= sequenceNumber) {
            "The value in the 'Retire Prior To' field in NEW_CONNECTION_ID frame " +
                "MUST be less than or equal to the value in the 'Sequence Number' field"
        }
        require(connectionID.size in 1..20) {
            "The size of the value in the 'Connection ID' field in NEW_CONNECTION_ID frame " +
                "MUST be at least 1 byte and at most 20 bytes"
        }
        require(statelessResetToken.size == PayloadSize.STATELESS_RESET_TOKEN) {
            "The size of the value in the 'Stateless Reset Token' field in NEW_CONNECTION_ID frame " +
                "MUST be 16 bytes"
        }

        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(sequenceNumber) +
                PayloadSize.ofVarInt(retirePriorTo) +
                PayloadSize.ofConnectionID(connectionID) +
                PayloadSize.STATELESS_RESET_TOKEN

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.NEW_CONNECTION_ID)
            writeVarInt(sequenceNumber)
            writeVarInt(retirePriorTo)
            writeConnectionID(connectionID)
            writeFully(statelessResetToken)
        }
    }

    override suspend fun writeRetireConnectionId(sequenceNumber: Long): Long {
        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.ofVarInt(sequenceNumber)) {
            writeFrameType(QUICFrameType.RETIRE_CONNECTION_ID)
            writeVarInt(sequenceNumber)
        }
    }

    override suspend fun writePathChallenge(data: ByteArray): Long {
        require(data.size == PayloadSize.PATH_CHALLENGE_DATA) {
            "The size of the value in the 'Data' field in PATH_CHALLENGE frame MUST be 8 bytes"
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.PATH_CHALLENGE_DATA) {
            writeFrameType(QUICFrameType.PATH_CHALLENGE)
            writeFully(data)
        }
    }

    override suspend fun writePathResponse(data: ByteArray): Long {
        require(data.size == PayloadSize.PATH_CHALLENGE_DATA) {
            "The size of the value in the 'Data' field in PATH_RESPONSE frame MUST be 8 bytes"
        }

        return writeFrame(PayloadSize.FRAME_TYPE_SIZE + PayloadSize.PATH_CHALLENGE_DATA) {
            writeFrameType(QUICFrameType.PATH_RESPONSE)
            writeFully(data)
        }
    }

    override suspend fun writeConnectionCloseWithTransportError(
        errorCode: QUICTransportError,
        frameTypeV1: QUICFrameType?,
        reasonPhrase: ByteArray,
    ): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofError(errorCode) +
                PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofByteArrayWithLength(reasonPhrase)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.CONNECTION_CLOSE_TRANSPORT_ERR)
            errorCode.writeToFrame(this)
            writeFrameType((frameTypeV1 ?: QUICFrameType.PADDING))
            writeVarInt(reasonPhrase.size)
            writeFully(reasonPhrase)
        }
    }

    override suspend fun writeConnectionCloseWithAppError(
        applicationProtocolErrorCode: AppError,
        reasonPhrase: ByteArray,
    ): Long {
        val expectedSize: Int =
            PayloadSize.FRAME_TYPE_SIZE +
                PayloadSize.ofVarInt(applicationProtocolErrorCode.intCode) +
                PayloadSize.ofByteArrayWithLength(reasonPhrase)

        return writeFrame(expectedSize) {
            writeFrameType(QUICFrameType.CONNECTION_CLOSE_APP_ERR)
            applicationProtocolErrorCode.writeToFrame(this)
            writeVarInt(reasonPhrase.size)
            writeFully(reasonPhrase)
        }
    }

    override suspend fun writeHandshakeDone(): Long = writeFrame(PayloadSize.FRAME_TYPE_SIZE) {
        writeFrameType(QUICFrameType.HANDSHAKE_DONE)
    }

    // HELPER FUNCTIONS AND VALUES

    private fun BytePacketBuilder.writeFrameType(typeV1: QUICFrameType) {
        // it is actually a varint with length 8, as frame types are all values in 0x00..0x1e
        writeUInt8(typeV1.typeValue)
    }
}
