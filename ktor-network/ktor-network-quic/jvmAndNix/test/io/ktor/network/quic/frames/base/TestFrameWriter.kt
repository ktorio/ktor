/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.utils.io.core.*

internal class TestFrameWriter : FrameWriter {
    private val _expectedFrames = mutableListOf<QUICFrameType>()
    val expectedFrames: List<QUICFrameType> = _expectedFrames
    var writtenFramesCnt: Int = 0
        private set

    @OptIn(ExperimentalUnsignedTypes::class)
    fun writeCustomFrame(
        packetBuilder: BytePacketBuilder,
        typeV1: QUICFrameType,
        shouldFailOnRead: Boolean,
        body: BytePacketBuilder.() -> Unit,
    ) = withLog(typeV1, shouldFailOnRead) {
        packetBuilder.writeUByte(typeV1.typeValue)
        packetBuilder.body()
    }

    override fun writePadding(
        packetBuilder: BytePacketBuilder,
    ) = withLog(QUICFrameType.PADDING) {
        writePadding(packetBuilder)
    }

    override fun writePing(
        packetBuilder: BytePacketBuilder,
    ) = withLog(QUICFrameType.PING) {
        writePing(packetBuilder)
    }

    override fun writeACK(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
    ) = withLog(QUICFrameType.ACK) {
        writeACK(packetBuilder, ackDelay, ack_delay_exponent, ackRanges)
    }

    override fun writeACKWithECN(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) = withLog(QUICFrameType.ACK_ECN) {
        writeACKWithECN(packetBuilder, ackDelay, ack_delay_exponent, ackRanges, ect0, ect1, ectCE)
    }

    override fun writeResetStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finaSize: Long,
    ) = withLog(QUICFrameType.RESET_STREAM) {
        writeResetStream(packetBuilder, streamId, applicationProtocolErrorCode, finaSize)
    }

    override fun writeStopSending(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ) = withLog(QUICFrameType.STOP_SENDING) {
        writeStopSending(packetBuilder, streamId, applicationProtocolErrorCode)
    }

    override fun writeCrypto(
        packetBuilder: BytePacketBuilder,
        offset: Long,
        data: ByteArray,
    ) = withLog(QUICFrameType.CRYPTO) {
        writeCrypto(packetBuilder, offset, data)
    }

    override fun writeNewToken(
        packetBuilder: BytePacketBuilder,
        token: ByteArray,
    ) = withLog(QUICFrameType.NEW_TOKEN) {
        writeNewToken(packetBuilder, token)
    }

    override fun writeStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        offset: Long?,
        specifyLength: Boolean,
        fin: Boolean,
        data: ByteArray,
    ) = withLog(QUICFrameType.STREAM) {
        writeStream(packetBuilder, streamId, offset, specifyLength, fin, data)
    }

    override fun writeMaxData(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = withLog(QUICFrameType.MAX_DATA) {
        writeMaxData(packetBuilder, maximumData)
    }

    override fun writeMaxStreamData(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = withLog(QUICFrameType.MAX_STREAM_DATA) {
        writeMaxStreamData(packetBuilder, streamId, maximumStreamData)
    }

    override fun writeMaxStreamsBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(QUICFrameType.MAX_STREAMS_BIDIRECTIONAL) {
        writeMaxStreamsBidirectional(packetBuilder, maximumStreams)
    }

    override fun writeMaxStreamsUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL) {
        writeMaxStreamsUnidirectional(packetBuilder, maximumStreams)
    }

    override fun writeDataBlocked(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = withLog(QUICFrameType.DATA_BLOCKED) {
        writeDataBlocked(packetBuilder, maximumData)
    }

    override fun writeStreamDataBlocked(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = withLog(QUICFrameType.STREAM_DATA_BLOCKED) {
        writeStreamDataBlocked(packetBuilder, streamId, maximumStreamData)
    }

    override fun writeStreamsBlockedBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL) {
        writeStreamsBlockedBidirectional(packetBuilder, maximumStreams)
    }

    override fun writeStreamsBlockedUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL) {
        writeStreamsBlockedUnidirectional(packetBuilder, maximumStreams)
    }

    override fun writeNewConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray,
    ) = withLog(QUICFrameType.NEW_CONNECTION_ID) {
        writeNewConnectionId(packetBuilder, sequenceNumber, retirePriorTo, connectionID, statelessResetToken)
    }

    override fun writeRetireConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
    ) = withLog(QUICFrameType.RETIRE_CONNECTION_ID) {
        writeRetireConnectionId(packetBuilder, sequenceNumber)
    }

    override fun writePathChallenge(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = withLog(QUICFrameType.PATH_CHALLENGE) {
        writePathChallenge(packetBuilder, data)
    }

    override fun writePathResponse(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = withLog(QUICFrameType.PATH_RESPONSE) {
        writePathResponse(packetBuilder, data)
    }

    override fun writeConnectionCloseWithTransportError(
        packetBuilder: BytePacketBuilder,
        errorCode: QUICTransportError,
        frameTypeV1: QUICFrameType?,
        reasonPhrase: ByteArray,
    ) = withLog(QUICFrameType.CONNECTION_CLOSE_TRANSPORT_ERR) {
        writeConnectionCloseWithTransportError(packetBuilder, errorCode, frameTypeV1, reasonPhrase)
    }

    override fun writeConnectionCloseWithAppError(
        packetBuilder: BytePacketBuilder,
        errorCode: AppError,
        reasonPhrase: ByteArray,
    ) = withLog(QUICFrameType.CONNECTION_CLOSE_APP_ERR) {
        writeConnectionCloseWithAppError(packetBuilder, errorCode, reasonPhrase)
    }

    override fun writeHandshakeDone(
        packetBuilder: BytePacketBuilder,
    ) = withLog(QUICFrameType.HANDSHAKE_DONE) {
        writeHandshakeDone(packetBuilder)
    }

    private fun withLog(typeV1: QUICFrameType, shouldFailOnRead: Boolean = false, body: FrameWriter.() -> Unit) {
        FrameWriterImpl.body()
        if (!shouldFailOnRead) {
            _expectedFrames.add(typeV1)
        }
        writtenFramesCnt++
    }
}
