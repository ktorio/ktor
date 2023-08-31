/* ktlint-disable standard:wrapping */

/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames.base

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.frames.*
import io.ktor.network.quic.packets.*
import kotlin.test.*

internal class TestFrameProcessor(
    private val validator: ReadFramesValidator,
    expectedFrames: List<QUICFrameType>,
) : FrameProcessor {
    private val expectedFrames = mutableListOf(*expectedFrames.toTypedArray())

    override suspend fun acceptPadding(packet: QUICPacket) = testAccept(QUICFrameType.PADDING)

    override suspend fun acceptPing(packet: QUICPacket) = testAccept(QUICFrameType.PING)

    override suspend fun acceptACK(
        packet: QUICPacket,
        ackDelay: Long,
        ackRanges: List<Long>,
    ) = testAccept(QUICFrameType.ACK) {
        listACKValidators[it](ackDelay, ackRanges)
    }

    override suspend fun acceptACKWithECN(
        packet: QUICPacket,
        ackDelay: Long,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) = testAccept(QUICFrameType.ACK_ECN) {
        listACKWithECNValidators[it](ackDelay, ackRanges, ect0, ect1, ectCE)
    }

    override suspend fun acceptResetStream(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ) = testAccept(QUICFrameType.RESET_STREAM) {
        listResetStreamValidators[it](streamId, applicationProtocolErrorCode, finalSize)
    }

    override suspend fun acceptStopSending(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ) = testAccept(QUICFrameType.STOP_SENDING) {
        listStopSendingValidators[it](streamId, applicationProtocolErrorCode)
    }

    override suspend fun acceptCrypto(
        packet: QUICPacket,
        offset: Long,
        cryptoData: ByteArray,
    ) = testAccept(QUICFrameType.CRYPTO) {
        listCryptoValidators[it](offset, cryptoData)
    }

    override suspend fun acceptNewToken(
        packet: QUICPacket,
        token: ByteArray,
    ) = testAccept(QUICFrameType.NEW_TOKEN) {
        listNewTokenValidators[it](token)
    }

    override suspend fun acceptStream(
        packet: QUICPacket,
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray,
    ) = testAccept(QUICFrameType.STREAM) {
        listStreamValidators[it](streamId, offset, fin, streamData)
    }

    override suspend fun acceptMaxData(
        packet: QUICPacket,
        maximumData: Long,
    ) = testAccept(QUICFrameType.MAX_DATA) {
        listMaxDataValidators[it](maximumData)
    }

    override suspend fun acceptMaxStreamData(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(QUICFrameType.MAX_STREAM_DATA) {
        listMaxStreamDataValidators[it](streamId, maximumStreamData)
    }

    override suspend fun acceptMaxStreamsBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ) = testAccept(QUICFrameType.MAX_STREAMS_BIDIRECTIONAL) {
        listMaxStreamsBidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptMaxStreamsUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ) = testAccept(QUICFrameType.MAX_STREAMS_UNIDIRECTIONAL) {
        listMaxStreamsUnidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptDataBlocked(
        packet: QUICPacket,
        maximumData: Long,
    ) = testAccept(QUICFrameType.DATA_BLOCKED) {
        listDataBlockedValidators[it](maximumData)
    }

    override suspend fun acceptStreamDataBlocked(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ) = testAccept(QUICFrameType.STREAM_DATA_BLOCKED) {
        listStreamDataBlockedValidators[it](streamId, maximumStreamData)
    }

    override suspend fun acceptStreamsBlockedBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ) = testAccept(QUICFrameType.STREAMS_BLOCKED_BIDIRECTIONAL) {
        listStreamsBlockedBidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptStreamsBlockedUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ) = testAccept(QUICFrameType.STREAMS_BLOCKED_UNIDIRECTIONAL) {
        listStreamsBlockedUnidirectionalValidators[it](maximumStreams)
    }

    override suspend fun acceptNewConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray?,
    ) = testAccept(QUICFrameType.NEW_CONNECTION_ID) {
        listNewConnectionIdValidators[it](sequenceNumber, retirePriorTo, connectionID, statelessResetToken)
    }

    override suspend fun acceptRetireConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
    ) = testAccept(QUICFrameType.RETIRE_CONNECTION_ID) {
        listRetireConnectionIdValidators[it](sequenceNumber)
    }

    override suspend fun acceptPathChallenge(
        packet: QUICPacket,
        data: ByteArray,
    ) = testAccept(QUICFrameType.PATH_CHALLENGE) {
        listPathChallengeValidators[it](data)
    }

    override suspend fun acceptPathResponse(
        packet: QUICPacket,
        data: ByteArray,
    ) = testAccept(QUICFrameType.PATH_RESPONSE) {
        listPathResponseValidators[it](data)
    }

    override suspend fun acceptConnectionCloseWithTransportError(
        packet: QUICPacket,
        errorCode: QUICTransportError,
        frameType: QUICFrameType,
        reasonPhrase: ByteArray,
    ) = testAccept(QUICFrameType.CONNECTION_CLOSE_TRANSPORT_ERR) {
        listConnectionCloseWithTransportErrorValidators[it](errorCode, frameType, reasonPhrase)
    }

    override suspend fun acceptConnectionCloseWithAppError(
        packet: QUICPacket,
        errorCode: AppError,
        reasonPhrase: ByteArray,
    ) = testAccept(QUICFrameType.CONNECTION_CLOSE_APP_ERR) {
        listConnectionCloseWithAppErrorValidators[it](errorCode, reasonPhrase)
    }

    override suspend fun acceptHandshakeDone(packet: QUICPacket) = testAccept(QUICFrameType.HANDSHAKE_DONE)

    private val mapAccess = mutableMapOf<QUICFrameType, Int>()

    private fun testAccept(typeV1: QUICFrameType, body: ReadFramesValidator.(Int) -> Unit = {}): QUICTransportError? {
        assertIsExpectedFrame(typeV1)
        val index = mapAccess.getOrPut(typeV1) { 0 }
        mapAccess[typeV1] = index + 1
        validator.body(index)
        return null
    }

    private fun assertIsExpectedFrame(typeV1: QUICFrameType) {
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
    val listACKValidators = mutableListOf<(
        ackDelay: Long,
        ackRanges: List<Long>,
    ) -> Unit>()

    val listACKWithECNValidators = mutableListOf<(
        ackDelay: Long,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ) -> Unit>()

    val listResetStreamValidators = mutableListOf<(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ) -> Unit>()

    val listStopSendingValidators = mutableListOf<(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ) -> Unit>()

    val listCryptoValidators = mutableListOf<(
        offset: Long,
        cryptoData: ByteArray,
    ) -> Unit>()

    val listNewTokenValidators = mutableListOf<(
        token: ByteArray,
    ) -> Unit>()

    val listStreamValidators = mutableListOf<(
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray,
    ) -> Unit>()

    val listMaxDataValidators = mutableListOf<(
        maximumData: Long,
    ) -> Unit>()

    val listMaxStreamDataValidators = mutableListOf<(
        streamId: Long,
        maximumStreamData: Long,
    ) -> Unit>()

    val listMaxStreamsBidirectionalValidators = mutableListOf<(
        maximumStreams: Long,
    ) -> Unit>()

    val listMaxStreamsUnidirectionalValidators = mutableListOf<(
        maximumStreams: Long,
    ) -> Unit>()

    val listDataBlockedValidators = mutableListOf<(
        maximumData: Long,
    ) -> Unit>()

    val listStreamDataBlockedValidators = mutableListOf<(
        streamId: Long,
        maximumStreamData: Long,
    ) -> Unit>()

    val listStreamsBlockedBidirectionalValidators = mutableListOf<(
        maximumStreams: Long,
    ) -> Unit>()

    val listStreamsBlockedUnidirectionalValidators = mutableListOf<(
        maximumStreams: Long,
    ) -> Unit>()

    val listNewConnectionIdValidators = mutableListOf<(
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray?,
    ) -> Unit>()

    val listRetireConnectionIdValidators = mutableListOf<(
        sequenceNumber: Long,
    ) -> Unit>()

    val listPathChallengeValidators = mutableListOf<(
        data: ByteArray,
    ) -> Unit>()

    val listPathResponseValidators = mutableListOf<(
        data: ByteArray,
    ) -> Unit>()

    val listConnectionCloseWithTransportErrorValidators = mutableListOf<(
        errorCode: QUICTransportError,
        frameType: QUICFrameType,
        reasonPhrase: ByteArray,
    ) -> Unit>()

    val listConnectionCloseWithAppErrorValidators = mutableListOf<(
        errorCode: AppError,
        reasonPhrase: ByteArray,
    ) -> Unit>()

    fun validateACK(body: (ackDelay: Long, ackRanges: List<Long>) -> Unit) {
        listACKValidators.add(body)
    }

    fun validateACKWithECN(body: (ackDelay: Long, ackRanges: List<Long>, ect0: Long, ect1: Long, ectCE: Long) -> Unit) {
        listACKWithECNValidators.add(body)
    }

    fun validateResetStream(body: (streamId: Long, applicationProtocolErrorCode: AppError, finalSize: Long) -> Unit) {
        listResetStreamValidators.add(body)
    }

    fun validateStopSending(body: (streamId: Long, applicationProtocolErrorCode: AppError) -> Unit) {
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

    fun validateNewConnectionId(
        body: (
            sequenceNumber: Long,
            retirePriorTo: Long,
            connectionID: QUICConnectionID,
            resetToken: ByteArray?,
        ) -> Unit,
    ) {
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

    fun validateConnectionCloseWithTransportError(
        body: (errorCode: QUICTransportError, frameType: QUICFrameType, reasonPhrase: ByteArray) -> Unit,
    ) {
        listConnectionCloseWithTransportErrorValidators.add(body)
    }

    fun validateConnectionCloseWithAppError(body: (errorCode: AppError, reasonPhrase: ByteArray) -> Unit) {
        listConnectionCloseWithAppErrorValidators.add(body)
    }
}
