/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.utils.io.core.*

internal class TestFrameWriter : FrameWriter {
    private val _writtenFrames = mutableListOf<FrameType_v1>()
    val writtenFrames: List<FrameType_v1> = _writtenFrames

    override fun writePadding(
        packetBuilder: BytePacketBuilder,
    ) = withLog(FrameType_v1.PADDING) {
        writePadding(packetBuilder)
    }

    override fun writePing(
        packetBuilder: BytePacketBuilder,
    ) = withLog(FrameType_v1.PING) {
        writePing(packetBuilder)
    }

    override fun writeACK(
        packetBuilder: BytePacketBuilder,
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: LongArray,
    ) = withLog(FrameType_v1.ACK) {
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
    ) = withLog(FrameType_v1.ACK) {
        writeACKWithECN(packetBuilder, ackDelay, ack_delay_exponent, ackRanges, ect0, ect1, ectCE)
    }

    override fun writeResetStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finaSize: Long,
    ) = withLog(FrameType_v1.RESET_STREAM) {
        writeResetStream(packetBuilder, streamId, applicationProtocolErrorCode, finaSize)
    }

    override fun writeStopSending(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    ) = withLog(FrameType_v1.STOP_SENDING) {
        writeStopSending(packetBuilder, streamId, applicationProtocolErrorCode)
    }

    override fun writeCrypto(
        packetBuilder: BytePacketBuilder,
        offset: Long,
        data: ByteArray,
    ) = withLog(FrameType_v1.CRYPTO) {
        writeCrypto(packetBuilder, offset, data)
    }

    override fun writeNewToken(
        packetBuilder: BytePacketBuilder,
        token: ByteArray,
    ) = withLog(FrameType_v1.NEW_TOKEN) {
        writeNewToken(packetBuilder, token)
    }

    override fun writeStream(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        offset: Long?,
        length: Long?,
        fin: Boolean,
        data: ByteArray,
    ) = withLog(FrameType_v1.STREAM) {
        writeStream(packetBuilder, streamId, offset, length, fin, data)
    }

    override fun writeMaxData(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = withLog(FrameType_v1.MAX_DATA) {
        writeMaxData(packetBuilder, maximumData)
    }

    override fun writeMaxStreamData(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = withLog(FrameType_v1.MAX_STREAM_DATA) {
        writeMaxStreamData(packetBuilder, streamId, maximumStreamData)
    }

    override fun writeMaxStreamsBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL) {
        writeMaxStreamsBidirectional(packetBuilder, maximumStreams)
    }

    override fun writeMaxStreamsUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL) {
        writeMaxStreamsUnidirectional(packetBuilder, maximumStreams)
    }

    override fun writeDataBlocked(
        packetBuilder: BytePacketBuilder,
        maximumData: Long,
    ) = withLog(FrameType_v1.DATA_BLOCKED) {
        writeDataBlocked(packetBuilder, maximumData)
    }

    override fun writeStreamDataBlocked(
        packetBuilder: BytePacketBuilder,
        streamId: Long,
        maximumStreamData: Long,
    ) = withLog(FrameType_v1.STREAM_DATA_BLOCKED) {
        writeStreamDataBlocked(packetBuilder, streamId, maximumStreamData)
    }

    override fun writeStreamsBlockedBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL) {
        writeStreamsBlockedBidirectional(packetBuilder, maximumStreams)
    }

    override fun writeStreamsBlockedUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: Long,
    ) = withLog(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL) {
        writeStreamsBlockedUnidirectional(packetBuilder, maximumStreams)
    }

    override fun writeNewConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ) = withLog(FrameType_v1.NEW_CONNECTION_ID) {
        writeNewConnectionId(packetBuilder, sequenceNumber, retirePriorTo, connectionId, statelessResetToken)
    }

    override fun writeRetireConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: Long,
    ) = withLog(FrameType_v1.RETIRE_CONNECTION_ID) {
        writeRetireConnectionId(packetBuilder, sequenceNumber)
    }

    override fun writePathChallenge(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = withLog(FrameType_v1.PATH_CHALLENGE) {
        writePathChallenge(packetBuilder, data)
    }

    override fun writePathResponse(
        packetBuilder: BytePacketBuilder,
        data: ByteArray,
    ) = withLog(FrameType_v1.PATH_RESPONSE) {
        writePathResponse(packetBuilder, data)
    }

    override fun writeConnectionCloseWithTransportError(
        packetBuilder: BytePacketBuilder,
        errorCode: QUICTransportError_v1,
        frameTypeV1: FrameType_v1,
        reasonPhrase: ByteArray,
    ) = withLog(FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR) {
        writeConnectionCloseWithTransportError(packetBuilder, errorCode, frameTypeV1, reasonPhrase)
    }

    override fun writeConnectionCloseWithAppError(
        packetBuilder: BytePacketBuilder,
        errorCode: AppError_v1,
        reasonPhrase: ByteArray,
    ) = withLog(FrameType_v1.CONNECTION_CLOSE_APP_ERR) {
        writeConnectionCloseWithAppError(packetBuilder, errorCode, reasonPhrase)
    }

    override fun writeHandshakeDone(
        packetBuilder: BytePacketBuilder,
    ) = withLog(FrameType_v1.HANDSHAKE_DONE) {
        writeHandshakeDone(packetBuilder)
    }

    private fun withLog(typeV1: FrameType_v1, body: FrameWriter.() -> Unit) {
        _writtenFrames.add(typeV1)
        FrameWriterImpl.body()
    }
}
