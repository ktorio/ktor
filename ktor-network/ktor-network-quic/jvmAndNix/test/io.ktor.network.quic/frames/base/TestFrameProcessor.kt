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
        validateACK(ackDelay, ackRanges)
    }

    override suspend fun acceptACKWithECN(
        ackDelay: Long,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) = testAccept(FrameType_v1.ACK) {
        validateACKWithECN(ackDelay, ackRanges, ect0, ect1, ectCE)
    }

    override suspend fun acceptResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finalSize: Long,
    ) = testAccept(FrameType_v1.RESET_STREAM) {
        validateResetStream(streamId, applicationProtocolErrorCode, finalSize)
    }

    override suspend fun acceptStopSending(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    ) = testAccept(FrameType_v1.STOP_SENDING) {
        validateStopSending(streamId, applicationProtocolErrorCode)
    }

    override suspend fun acceptCrypto(
        offset: Long,
        length: Long,
        cryptoData: ByteArray,
    ) = testAccept(FrameType_v1.CRYPTO) {
        validateCrypto(offset, length, cryptoData)
    }

    override suspend fun acceptNewToken(
        tokenLength: Long,
        token: ByteArray,
    ) = testAccept(FrameType_v1.NEW_TOKEN) {
        validateNewToken(tokenLength, token)
    }

    override suspend fun acceptStream(
        streamId: Long,
        offset: Long,
        length: Long,
        fin: Boolean,
        streamData: ByteArray,
    ) = testAccept(FrameType_v1.STREAM) {
        validateStream(streamId, offset, length, fin, streamData)
    }

    override suspend fun acceptMaxData(
        maximumData: Long,
    ) = testAccept(FrameType_v1.MAX_DATA) {
        validateMaxData(maximumData)
    }

    override suspend fun acceptMaxStreamData(
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(FrameType_v1.MAX_STREAM_DATA) {
        validateMaxStreamData(streamId, maximumStreamData)
    }

    override suspend fun acceptMaxStreamsBidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL) {
        validateMaxStreamsBidirectional(maximumStreams)
    }

    override suspend fun acceptMaxStreamsUnidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL) {
        validateMaxStreamsUnidirectional(maximumStreams)
    }

    override suspend fun acceptDataBlocked(
        maximumData: Long,
    ) = testAccept(FrameType_v1.DATA_BLOCKED) {
        validateDataBlocked(maximumData)
    }

    override suspend fun acceptStreamDataBlocked(
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(FrameType_v1.STREAM_DATA_BLOCKED) {
        validateStreamDataBlocked(streamId, maximumStreamData)
    }

    override suspend fun acceptStreamsBlockedBidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL) {
        validateStreamsBlockedBidirectional(maximumStreams)
    }

    override suspend fun acceptStreamsBlockedUnidirectional(
        maximumStreams: Long,
    ) = testAccept(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL) {
        validateStreamsBlockedUnidirectional(maximumStreams)
    }

    override suspend fun acceptNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        length: Int,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ) = testAccept(FrameType_v1.NEW_CONNECTION_ID) {
        validateNewConnectionId(sequenceNumber, retirePriorTo, length, connectionId, statelessResetToken)
    }

    override suspend fun acceptRetireConnectionId(
        sequenceNumber: Long,
    ) = testAccept(FrameType_v1.RETIRE_CONNECTION_ID) {
        validateRetireConnectionId(sequenceNumber)
    }

    override suspend fun acceptPathChallenge(
        data: ByteArray,
    ) = testAccept(FrameType_v1.PATH_CHALLENGE) {
        validatePathChallenge(data)
    }

    override suspend fun acceptPathResponse(
        data: ByteArray,
    ) = testAccept(FrameType_v1.PATH_RESPONSE) {
        validatePathResponse(data)
    }

    override suspend fun acceptConnectionCloseWithTransportError(
        errorCode: QUICTransportError_v1,
        frameType: FrameType_v1,
        reasonPhrase: ByteArray,
    ) = testAccept(FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR) {
        validateConnectionCloseWithTransportError(errorCode, frameType, reasonPhrase)
    }

    override suspend fun acceptConnectionCloseWithAppError(
        errorCode: AppError_v1,
        reasonPhrase: ByteArray,
    ) = testAccept(FrameType_v1.CONNECTION_CLOSE_APP_ERR) {
        validateConnectionCloseWithAppError(errorCode, reasonPhrase)
    }

    override suspend fun acceptHandshakeDone() = testAccept(FrameType_v1.HANDSHAKE_DONE)

    private fun testAccept(typeV1: FrameType_v1, body: ReadFramesValidator.() -> Unit = {}): QUICTransportError_v1? {
        assertIsExpectedFrame(typeV1)
        validator.body()
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
    var validateACK: (ackDelay: Long, ackRanges: LongArray) -> Unit =
        { _, _ -> emptyValidatorFail() }
    var validateACKWithECN: (ackDelay: Long, ackRanges: LongArray, ect0: Long, ect1: Long, ectCE: Long) -> Unit =
        { _, _, _, _, _ -> emptyValidatorFail() }
    var validateResetStream: (streamId: Long, applicationProtocolErrorCode: AppError_v1, finalSize: Long) -> Unit =
        { _, _, _ -> emptyValidatorFail() }
    var validateStopSending: (streamId: Long, applicationProtocolErrorCode: AppError_v1) -> Unit =
        { _, _ -> emptyValidatorFail() }
    var validateCrypto: (offset: Long, length: Long, cryptoData: ByteArray) -> Unit =
        { _, _, _ -> emptyValidatorFail() }
    var validateNewToken: (tokenLength: Long, token: ByteArray) -> Unit =
        { _, _ -> emptyValidatorFail() }
    var validateStream: (streamId: Long, offset: Long, length: Long, fin: Boolean, streamData: ByteArray) -> Unit =
        { _, _, _, _, _ -> emptyValidatorFail() }
    var validateMaxData: (maximumData: Long) -> Unit =
        { emptyValidatorFail() }
    var validateMaxStreamData: (streamId: Long, maximumStreamData: Long) -> Unit =
        { _, _ -> emptyValidatorFail() }
    var validateMaxStreamsBidirectional: (maximumStreams: Long) -> Unit =
        { emptyValidatorFail() }
    var validateMaxStreamsUnidirectional: (maximumStreams: Long) -> Unit =
        { emptyValidatorFail() }
    var validateDataBlocked: (maximumData: Long) -> Unit =
        { emptyValidatorFail() }
    var validateStreamDataBlocked: (streamId: Long, maximumStreamData: Long) -> Unit =
        { _, _ -> emptyValidatorFail() }
    var validateStreamsBlockedBidirectional: (maximumStreams: Long) -> Unit =
        { emptyValidatorFail() }
    var validateStreamsBlockedUnidirectional: (maximumStreams: Long) -> Unit =
        { emptyValidatorFail() }
    var validateNewConnectionId: (sequenceNumber: Long, retirePriorTo: Long, length: Int, connectionId: ByteArray, statelessResetToken: ByteArray) -> Unit =
        { _, _, _, _, _ -> emptyValidatorFail() }
    var validateRetireConnectionId: (sequenceNumber: Long) -> Unit =
        { emptyValidatorFail() }
    var validatePathChallenge: (data: ByteArray) -> Unit =
        { emptyValidatorFail() }
    var validatePathResponse: (data: ByteArray) -> Unit =
        { emptyValidatorFail() }
    var validateConnectionCloseWithTransportError: (errorCode: QUICTransportError_v1, frameType: FrameType_v1, reasonPhrase: ByteArray) -> Unit =
        { _, _, _ -> emptyValidatorFail() }
    var validateConnectionCloseWithAppError: (errorCode: AppError_v1, reasonPhrase: ByteArray) -> Unit =
        { _, _ -> emptyValidatorFail() }

    private fun emptyValidatorFail(): Nothing = error("Called validator's body is empty")
}
