/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.packets.*

internal interface FrameProcessor {
    suspend fun acceptPadding(packet: QUICPacket): QUICTransportError_v1?

    suspend fun acceptPing(packet: QUICPacket): QUICTransportError_v1?

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is composed of **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun acceptACK(
        packet: QUICPacket,
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
        packet: QUICPacket,
        ackDelay: Long,
        ackRanges: LongArray,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): QUICTransportError_v1?

    suspend fun acceptResetStream(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStopSending(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ): QUICTransportError_v1?

    suspend fun acceptCrypto(
        packet: QUICPacket,
        offset: Long,
        cryptoData: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptNewToken(
        packet: QUICPacket,
        token: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptStream(
        packet: QUICPacket,
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptMaxData(
        packet: QUICPacket,
        maximumData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamData(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamsBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptMaxStreamsUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptDataBlocked(
        packet: QUICPacket,
        maximumData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamDataBlocked(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamsBlockedBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptStreamsBlockedUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError_v1?

    suspend fun acceptNewConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: ConnectionID,
        statelessResetToken: ByteArray?,
    ): QUICTransportError_v1?

    suspend fun acceptRetireConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
    ): QUICTransportError_v1?

    suspend fun acceptPathChallenge(
        packet: QUICPacket,
        data: ByteArray,
    ): QUICTransportError_v1?

    suspend fun acceptPathResponse(
        packet: QUICPacket,
        data: ByteArray,
    ): QUICTransportError_v1?

    suspend fun acceptConnectionCloseWithTransportError(
        packet: QUICPacket,
        errorCode: QUICTransportError_v1,
        frameType: FrameType_v1,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptConnectionCloseWithAppError(
        packet: QUICPacket,
        errorCode: AppError,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError_v1?

    suspend fun acceptHandshakeDone(packet: QUICPacket): QUICTransportError_v1?
}
