/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.errors.*

internal interface FrameProcessor {
    suspend fun acceptPadding(): QUICTransportError_v1?

    suspend fun acceptPing(): QUICTransportError_v1?

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is composed of **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun acceptACK(
        ackDelay: Long,
        ackRanges: LongArray,
    ): QUICTransportError_v1?

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is composed of **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun acceptACKWithECN(
        ackDelay: Long,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): QUICTransportError_v1?

    suspend fun acceptResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
        finalSize: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStopSending(
        streamId: Long,
        applicationProtocolErrorCode: AppError_v1,
    ): QUICTransportError_v1?

    suspend fun acceptCrypto(
        offset: Long,
        length: Long,
        cryptoData: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptNewToken(
        tokenLength: Long,
        token: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptStream(
        streamId: Long,
        offset: Long,
        length: Long,
        fin: Boolean,
        streamData: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptMaxData(
        maximumData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamData(
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamsBidirectional(
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamsUnidirectional(
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptDataBlocked(
        maximumData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamDataBlocked(
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamsBlockedBidirectional(
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamsBlockedUnidirectional(
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptNewConnectionId(
        sequenceNumber: Long,
        retirePriorTo: Long,
        length: Byte,
        connectionId: ByteArray,
        statelessResetToken: ByteArray,
    ): QUICTransportError_v1?

    suspend fun acceptRetireConnectionId(
        sequenceNumber: Long,
    ): QUICTransportError_v1?

    suspend fun acceptPathChallenge(
        data: ByteArray,
    ): QUICTransportError_v1?

    suspend fun acceptPathResponse(
        data: ByteArray,
    ): QUICTransportError_v1?

    suspend fun acceptConnectionCloseWithTransportError(
        errorCode: QUICTransportError_v1,
        frameType: FrameType_v1,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptConnectionCloseWithAppError(
        errorCode: AppError_v1,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptHandshakeDone(): QUICTransportError_v1?
}
