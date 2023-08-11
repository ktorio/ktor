/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("LocalVariableName")

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.utils.io.core.*

internal class TestFrameWriter(handler: TestPacketSendHandler) : FrameWriter {
    private val _expectedFrames = mutableListOf<QUICFrameType>()
    val expectedFrames: List<QUICFrameType> = _expectedFrames
    var writtenFramesCnt: Int = 0
        private set

    private val frameWriter = FrameWriterImpl(handler)

    @OptIn(ExperimentalUnsignedTypes::class)
    suspend fun writeCustomFrame(
        packetBuilder: BytePacketBuilder,
        typeV1: QUICFrameType,
        shouldFailOnRead: Boolean,
        body: BytePacketBuilder.() -> Unit,
    ): Long = withLog(typeV1, shouldFailOnRead) {
        packetBuilder.writeUByte(typeV1.typeValue)
        packetBuilder.body()
        0 // packet number, not needed for these tests
    }

    override suspend fun writePadding(): Long {
        return withLog(QUICFrameType.PADDING) {
            writePadding()
        }
    }

    override suspend fun writePing(): Long {
        return withLog(QUICFrameType.PING) {
            writePing()
        }
    }

    override suspend fun writeACK(ackDelay: Long, ack_delay_exponent: Int, ackRanges: List<Long>): Long {
        return withLog(QUICFrameType.ACK) {
            writeACK(ackDelay, ack_delay_exponent, ackRanges)
        }
    }

    override suspend fun writeACKWithECN(
        ackDelay: Long,
        ack_delay_exponent: Int,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): Long = withLog(QUICFrameType.ACK_ECN) {
        writeACKWithECN(ackDelay, ack_delay_exponent, ackRanges, ect0, ect1, ectCE)
    }

    override suspend fun writeResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finaSize: Long,
    ): Long = withLog(QUICFrameType.RESET_STREAM) {
        writeResetStream(streamId, applicationProtocolErrorCode, finaSize)
    }

    override suspend fun writeStopSending(streamId: Long, applicationProtocolErrorCode: AppError): Long {
        return withLog(QUICFrameType.STOP_SENDING) {
            writeStopSending(streamId, applicationProtocolErrorCode)
        }
    }

    override suspend fun writeCrypto(offset: Long, data: ByteArray): Long {
        return withLog(QUICFrameType.CRYPTO) {
            writeCrypto(offset, data)
        }
    }

    override suspend fun writeNewToken(token: ByteArray): Long {
        return withLog(QUICFrameType.NEW_TOKEN) {
            writeNewToken(token)
        }
    }

    override suspend fun writeStream(
        streamId: Long,
        offset: Long?,
        specifyLength: Boolean,
        fin: Boolean,
        data: ByteArray,
    ): Long = withLog(QUICFrameType.STREAM) {
        writeStream(streamId, offset, specifyLength, fin, data)
    }

    override suspend fun writeMaxData(maximumData: Long): Long {
        return withLog(QUICFrameType.MAX_DATA) {
            writeMaxData(maximumData)
        }
    }

    override suspend fun writeMaxStreamData(streamId: Long, maximumStreamData: Long): Long {
        return withLog(QUICFrameType.MAX_STREAM_DATA) {
            writeMaxStreamData(streamId, maximumStreamData)
        }
    }

    override suspend fun writeMaxStreamsBidirectional(maximumStreams: Long): Long {
        return withLog(QUICFrameType.MAX_STREAMS_BIDIRECTIONAL) {
            writeMaxStreamsBidirectional(maximumStreams)
        }
    }

    override suspend fun writeMaxStreamsUnidirectional(maximumStreams: Long): Long {
        return withLog(QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL) {
            writeMaxStreamsUnidirectional(maximumStreams)
        }
    }

    override suspend fun writeDataBlocked(maximumData: Long): Long {
        return withLog(QUICFrameType.DATA_BLOCKED) {
            writeDataBlocked(maximumData)
        }
    }

    override suspend fun writeStreamDataBlocked(streamId: Long, maximumStreamData: Long): Long {
        return withLog(QUICFrameType.STREAM_DATA_BLOCKED) {
            writeStreamDataBlocked(streamId, maximumStreamData)
        }
    }

    override suspend fun writeStreamsBlockedBidirectional(maximumStreams: Long): Long {
        return withLog(QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL) {
            writeStreamsBlockedBidirectional(maximumStreams)
        }
    }

    override suspend fun writeStreamsBlockedUnidirectional(maximumStreams: Long): Long {
        return withLog(QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL) {
            writeStreamsBlockedUnidirectional(maximumStreams)
        }
    }

    override suspend fun writeNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray,
    ): Long = withLog(QUICFrameType.NEW_CONNECTION_ID) {
        writeNewConnectionId(sequenceNumber, retirePriorTo, connectionID, statelessResetToken)
    }

    override suspend fun writeRetireConnectionId(sequenceNumber: Long): Long {
        return withLog(QUICFrameType.RETIRE_CONNECTION_ID) {
            writeRetireConnectionId(sequenceNumber)
        }
    }

    override suspend fun writePathChallenge(data: ByteArray): Long {
        return withLog(QUICFrameType.PATH_CHALLENGE) {
            writePathChallenge(data)
        }
    }

    override suspend fun writePathResponse(data: ByteArray): Long {
        return withLog(QUICFrameType.PATH_RESPONSE) {
            writePathResponse(data)
        }
    }

    override suspend fun writeConnectionCloseWithTransportError(
        errorCode: QUICTransportError,
        frameTypeV1: QUICFrameType?,
        reasonPhrase: ByteArray,
    ): Long = withLog(QUICFrameType.CONNECTION_CLOSE_TRANSPORT_ERR) {
        writeConnectionCloseWithTransportError(errorCode, frameTypeV1, reasonPhrase)
    }

    override suspend fun writeConnectionCloseWithAppError(
        applicationProtocolErrorCode: AppError,
        reasonPhrase: ByteArray,
    ): Long = withLog(QUICFrameType.CONNECTION_CLOSE_APP_ERR) {
        writeConnectionCloseWithAppError(applicationProtocolErrorCode, reasonPhrase)
    }

    override suspend fun writeHandshakeDone(): Long {
        return withLog(QUICFrameType.HANDSHAKE_DONE) {
            writeHandshakeDone()
        }
    }

    private suspend fun withLog(
        typeV1: QUICFrameType,
        shouldFailOnRead: Boolean = false,
        body: suspend FrameWriter.() -> Long,
    ): Long {
        val packetNumber = frameWriter.body()

        if (!shouldFailOnRead) {
            _expectedFrames.add(typeV1)
        }
        writtenFramesCnt++

        return packetNumber
    }
}

internal class TestPacketSendHandler(private val builder: BytePacketBuilder) : PacketSendHandler {
    override suspend fun writeFrame(expectedFrameSize: Int, handler: BytePacketBuilder.() -> Unit): Long {
        builder.handler()

        return 0
    }
}
