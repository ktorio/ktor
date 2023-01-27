/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import kotlin.test.*

internal class TestFrameProcessor(
    private val validator: ReadFramesValidator,
    expectedFrames: List<FrameType_v1>,
) : FrameProcessor {
    private val expectedFrames = mutableListOf(*expectedFrames.toTypedArray())

    override suspend fun acceptPadding() = testAccept(FrameType_v1.PADDING)

    override suspend fun acceptPing() = testAccept(FrameType_v1.PING)

    override suspend fun acceptACK(
        ackDelay: Long,
        ackRanges: LongArray,
    ) = testAccept(FrameType_v1.ACK) {
        listACKValidators[it](ackDelay, ackRanges)
    }

    override suspend fun acceptACKWithECN(
        ackDelay: Long,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) = testAccept(FrameType_v1.ACK_ECN) {
        listACKWithECNValidators[it](ackDelay, ackRanges, ect0, ect1, ectCE)
    }

    override suspend fun acceptResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finalSize: Long,
    ) = testAccept(FrameType_v1.RESET_STREAM) {
        listResetStreamValidators[it](streamId, applicationProtocolErrorCode, finalSize)
    }

    override suspend fun acceptStopSending(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    ) = testAccept(FrameType_v1.STOP_SENDING) {
        listStopSendingValidators[it](streamId, applicationProtocolErrorCode)
    }

    override suspend fun acceptCrypto(
        offset: Long,
        cryptoData: ByteArray,
    ) = testAccept(FrameType_v1.CRYPTO) {
        listCryptoValidators[it](offset, cryptoData)
    }

    override suspend fun acceptNewToken(
        token: ByteArray,
    ) = testAccept(FrameType_v1.NEW_TOKEN) {
        listNewTokenValidators[it](token)
    }

    override suspend fun acceptStream(
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray,
    ) = testAccept(FrameType_v1.STREAM) {
        listStreamValidators[it](streamId, offset, fin, streamData)
    }

    override suspend fun acceptMaxData(
        maximumData: Long,
    ) = testAccept(FrameType_v1.MAX_DATA) {
        listMaxDataValidators[it](maximumData)
    }

    override suspend fun acceptMaxStreamData(
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(FrameType_v1.MAX_STREAM_DATA) {
        listMaxStreamDataValidators[it](streamId, maximumStreamData)
    }

    override suspend fun acceptMaxStreamsBidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL) {
        listMaxStreamsBidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptMaxStreamsUnidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL) {
        listMaxStreamsUnidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptDataBlocked(
        maximumData: Long,
    ) = testAccept(FrameType_v1.DATA_BLOCKED) {
        listDataBlockedValidators[it](maximumData)
    }

    override suspend fun acceptStreamDataBlocked(
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(FrameType_v1.STREAM_DATA_BLOCKED) {
        listStreamDataBlockedValidators[it](streamId, maximumStreamData)
    }

    override suspend fun acceptStreamsBlockedBidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL) {
        listStreamsBlockedBidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptStreamsBlockedUnidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL) {
        listStreamsBlockedUnidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ) = testAccept(FrameType_v1.NEW_CONNECTION_ID) {
        listNewConnectionIdValidators[it](sequenceNumber, retirePriorTo, connectionId, statelessResetToken)
    }

    override suspend fun acceptRetireConnectionId(
        sequenceNumber: Long,
    ) = testAccept(FrameType_v1.RETIRE_CONNECTION_ID) {
        listRetireConnectionIdValidators[it](sequenceNumber)
    }

    override suspend fun acceptPathChallenge(
        data: ByteArray,
    ) = testAccept(FrameType_v1.PATH_CHALLENGE) {
        listPathChallengeValidators[it](data)
    }

    override suspend fun acceptPathResponse(
        data: ByteArray,
    ) = testAccept(FrameType_v1.PATH_RESPONSE) {
        listPathResponseValidators[it](data)
    }

    override suspend fun acceptConnectionCloseWithTransportError(
        errorCode: QUICTransportError_v1,
        frameType: FrameType_v1,
        reasonPhrase: ByteArray,
    ) = testAccept(FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR) {
        listConnectionCloseWithTransportErrorValidators[it](errorCode, frameType, reasonPhrase)
    }

    override suspend fun acceptConnectionCloseWithAppError(
        errorCode: AppError_v1,
        reasonPhrase: ByteArray,
    ) = testAccept(FrameType_v1.CONNECTION_CLOSE_APP_ERR) {
        listConnectionCloseWithAppErrorValidators[it](errorCode, reasonPhrase)
    }

    override suspend fun acceptHandshakeDone() = testAccept(FrameType_v1.HANDSHAKE_DONE)

    private val mapAccess = mutableMapOf<FrameType_v1, Int>()

    private fun testAccept(typeV1: FrameType_v1, body: ReadFramesValidator.(Int) -> Unit = {}): QUICTransportError_v1? {
        assertIsExpectedFrame(typeV1)
        val index = mapAccess.getOrPut(typeV1) { 0 }
        mapAccess[typeV1] = index + 1
        validator.body(index)
        return null
    }

    private fun assertIsExpectedFrame(typeV1: FrameType_v1) {
        assertTrue(expectedFrames.isNotEmpty(), "No frames are expected, got: $typeV1")
        assertEquals(expectedFrames.removeFirst(), typeV1)
    }

    fun assertNoExpectedFramesLeft() {
        assertEquals(
            expected = 0,
            actual = expectedFrames.size,
            message = "Where are expected frame, that were not read: ${expectedFrames.joinToString()}"
        )
    }
}

internal class ReadFramesValidator {
    val listACKValidators = mutableListOf<(ackDelay: Long, ackRanges: LongArray) -> Unit>()
    val listACKWithECNValidators = mutableListOf<(ackDelay: Long, ackRanges: LongArray, ect0: Long, ect1: Long, ectCE: Long) -> Unit>()
    val listResetStreamValidators = mutableListOf<(streamId: Long, applicationProtocolErrorCode: AppError_v1, finalSize: Long) -> Unit>()
    val listStopSendingValidators = mutableListOf<(streamId: Long, applicationProtocolErrorCode: AppError_v1) -> Unit>()
    val listCryptoValidators = mutableListOf<(offset: Long, cryptoData: ByteArray) -> Unit>()
    val listNewTokenValidators = mutableListOf<(token: ByteArray) -> Unit>()
    val listStreamValidators = mutableListOf<(streamId: Long, offset: Long, fin: Boolean, streamData: ByteArray) -> Unit>()
    val listMaxDataValidators = mutableListOf<(maximumData: Long) -> Unit>()
    val listMaxStreamDataValidators = mutableListOf<(streamId: Long, maximumStreamData: Long) -> Unit>()
    val listMaxStreamsBidirectionalValidators = mutableListOf<(maximumStreams: Long) -> Unit>()
    val listMaxStreamsUnidirectionalValidators = mutableListOf<(maximumStreams: Long) -> Unit>()
    val listDataBlockedValidators = mutableListOf<(maximumData: Long) -> Unit>()
    val listStreamDataBlockedValidators = mutableListOf<(streamId: Long, maximumStreamData: Long) -> Unit>()
    val listStreamsBlockedBidirectionalValidators = mutableListOf<(maximumStreams: Long) -> Unit>()
    val listStreamsBlockedUnidirectionalValidators = mutableListOf<(maximumStreams: Long) -> Unit>()
    val listNewConnectionIdValidators = mutableListOf<(sequenceNumber: Long, retirePriorTo: Long, connectionId: ByteArray, statelessResetToken: ByteArray) -> Unit>()
    val listRetireConnectionIdValidators = mutableListOf<(sequenceNumber: Long) -> Unit>()
    val listPathChallengeValidators = mutableListOf<(data: ByteArray) -> Unit>()
    val listPathResponseValidators = mutableListOf<(data: ByteArray) -> Unit>()
    val listConnectionCloseWithTransportErrorValidators = mutableListOf<(errorCode: QUICTransportError_v1, frameType: FrameType_v1, reasonPhrase: ByteArray) -> Unit>()
    val listConnectionCloseWithAppErrorValidators = mutableListOf<(errorCode: AppError_v1, reasonPhrase: ByteArray) -> Unit>()

    fun validateACK(body: (ackDelay: Long, ackRanges: LongArray) -> Unit) {
        listACKValidators.add(body)
    }

    fun validateACKWithECN(body: (ackDelay: Long, ackRanges: LongArray, ect0: Long, ect1: Long, ectCE: Long) -> Unit) {
        listACKWithECNValidators.add(body)
    }

    fun validateResetStream(body: (streamId: Long, applicationProtocolErrorCode: AppError_v1, finalSize: Long) -> Unit) {
        listResetStreamValidators.add(body)
    }

    fun validateStopSending(body: (streamId: Long, applicationProtocolErrorCode: AppError_v1) -> Unit) {
        listStopSendingValidators.add(body)
    }

    fun validateCrypto(body: (offset: Long, cryptoData: ByteArray) -> Unit) {
        listCryptoValidators.add(body)
    }

    fun validateNewToken(body: (token: ByteArray) -> Unit) {
        listNewTokenValidators.add(body)
    }

    fun validateStream(body: (streamId: Long, offset: Long, fin: Boolean, streamData: ByteArray) -> Unit) {
        listStreamValidators.add(body)
    }

    fun validateMaxData(body: (maximumData: Long) -> Unit) {
        listMaxDataValidators.add(body)
    }

    fun validateMaxStreamData(body: (streamId: Long, maximumStreamData: Long) -> Unit) {
        listMaxStreamDataValidators.add(body)
    }

    fun validateMaxStreamsBidirectional(body: (maximumStreams: Long) -> Unit) {
        listMaxStreamsBidirectionalValidators.add(body)
    }

    fun validateMaxStreamsUnidirectional(body: (maximumStreams: Long) -> Unit) {
        listMaxStreamsUnidirectionalValidators.add(body)
    }

    fun validateDataBlocked(body: (maximumData: Long) -> Unit) {
        listDataBlockedValidators.add(body)
    }

    fun validateStreamDataBlocked(body: (streamId: Long, maximumStreamData: Long) -> Unit) {
        listStreamDataBlockedValidators.add(body)
    }

    fun validateStreamsBlockedBidirectional(body: (maximumStreams: Long) -> Unit) {
        listStreamsBlockedBidirectionalValidators.add(body)
    }

    fun validateStreamsBlockedUnidirectional(body: (maximumStreams: Long) -> Unit) {
        listStreamsBlockedUnidirectionalValidators.add(body)
    }

    fun validateNewConnectionId(body: (sequenceNumber: Long, retirePriorTo: Long, connectionId: ByteArray, statelessResetToken: ByteArray) -> Unit) {
        listNewConnectionIdValidators.add(body)
    }

    fun validateRetireConnectionId(body: (sequenceNumber: Long) -> Unit) {
        listRetireConnectionIdValidators.add(body)
    }

    fun validatePathChallenge(body: (data: ByteArray) -> Unit) {
        listPathChallengeValidators.add(body)
    }

    fun validatePathResponse(body: (data: ByteArray) -> Unit) {
        listPathResponseValidators.add(body)
    }

    fun validateConnectionCloseWithTransportError(body: (errorCode: QUICTransportError_v1, frameType: FrameType_v1, reasonPhrase: ByteArray) -> Unit) {
        listConnectionCloseWithTransportErrorValidators.add(body)
    }

    fun validateConnectionCloseWithAppError(body: (errorCode: AppError_v1, reasonPhrase: ByteArray) -> Unit) {
        listConnectionCloseWithAppErrorValidators.add(body)
    }
}
