/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.packets.*

internal interface FrameProcessor {
    suspend fun acceptPadding(packet: QUICPacket): QUICTransportError?

    suspend fun acceptPing(packet: QUICPacket): QUICTransportError?

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is composed of **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun acceptACK(
        packet: QUICPacket,
        ackDelay: Long,
        ackRanges: List<Long>,
    ): QUICTransportError?

    /**
     * @param ackRanges sorted ends in descending order of packet numbers ranges acknowledged by this frame.
     * Example: (18, 16, 14, 12, 10, 8, 6, 4) - here ranges are (18, 16), (14, 12), (10, 8), (6, 4) (both ends are inclusive)
     * [ackRanges] is composed of **largestAcknowledged**, **ackRangeCount**, **firstACKRange**, **ackRange...**
     * fields of the ACK frame
     */
    suspend fun acceptACKWithECN(
        packet: QUICPacket,
        ackDelay: Long,
        ackRanges: List<Long>,
        ect0: Long,
        ect1: Long,
        ectCE: Long,
    ): QUICTransportError?

    suspend fun acceptResetStream(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ): QUICTransportError?

    suspend fun acceptStopSending(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ): QUICTransportError?

    suspend fun acceptCrypto(
        packet: QUICPacket,
        offset: Long,
        cryptoData: ByteArray, // todo remove allocation?
    ): QUICTransportError?

    suspend fun acceptNewToken(
        packet: QUICPacket,
        token: ByteArray, // todo remove allocation?
    ): QUICTransportError?

    suspend fun acceptStream(
        packet: QUICPacket,
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray, // todo remove allocation?
    ): QUICTransportError?

    suspend fun acceptMaxData(
        packet: QUICPacket,
        maximumData: Long,
    ): QUICTransportError?

    suspend fun acceptMaxStreamData(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError?

    suspend fun acceptMaxStreamsBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError?

    suspend fun acceptMaxStreamsUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError?

    suspend fun acceptDataBlocked(
        packet: QUICPacket,
        maximumData: Long,
    ): QUICTransportError?

    suspend fun acceptStreamDataBlocked(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QUICTransportError?

    suspend fun acceptStreamsBlockedBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError?

    suspend fun acceptStreamsBlockedUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QUICTransportError?

    suspend fun acceptNewConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: QUICConnectionID,
        statelessResetToken: ByteArray?,
    ): QUICTransportError?

    suspend fun acceptRetireConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
    ): QUICTransportError?

    suspend fun acceptPathChallenge(
        packet: QUICPacket,
        data: ByteArray,
    ): QUICTransportError?

    suspend fun acceptPathResponse(
        packet: QUICPacket,
        data: ByteArray,
    ): QUICTransportError?

    suspend fun acceptConnectionCloseWithTransportError(
        packet: QUICPacket,
        errorCode: QUICTransportError,
        frameType: QUICFrameType,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError?

    suspend fun acceptConnectionCloseWithAppError(
        packet: QUICPacket,
        errorCode: AppError,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QUICTransportError?

    suspend fun acceptHandshakeDone(packet: QUICPacket): QUICTransportError?
}
