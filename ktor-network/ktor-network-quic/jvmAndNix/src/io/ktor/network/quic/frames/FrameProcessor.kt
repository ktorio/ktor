/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.frames

import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.network.quic.packets.*

internal interface FrameProcessor {
    suspend fun acceptPadding(packet: QUICPacket): QuicTransportError?

    suspend fun acceptPing(packet: QUICPacket): QuicTransportError?

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
    ): QuicTransportError?

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
    ): QuicTransportError?

    suspend fun acceptResetStream(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ): QuicTransportError?

    suspend fun acceptStopSending(
        packet: QUICPacket,
        streamId: Long,
        applicationProtocolErrorCode: AppError,
    ): QuicTransportError?

    suspend fun acceptCrypto(
        packet: QUICPacket,
        offset: Long,
        cryptoData: ByteArray, // todo remove allocation?
    ): QuicTransportError?

    suspend fun acceptNewToken(
        packet: QUICPacket,
        token: ByteArray, // todo remove allocation?
    ): QuicTransportError?

    suspend fun acceptStream(
        packet: QUICPacket,
        streamId: Long,
        offset: Long,
        fin: Boolean,
        streamData: ByteArray, // todo remove allocation?
    ): QuicTransportError?

    suspend fun acceptMaxData(
        packet: QUICPacket,
        maximumData: Long,
    ): QuicTransportError?

    suspend fun acceptMaxStreamData(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QuicTransportError?

    suspend fun acceptMaxStreamsBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QuicTransportError?

    suspend fun acceptMaxStreamsUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QuicTransportError?

    suspend fun acceptDataBlocked(
        packet: QUICPacket,
        maximumData: Long,
    ): QuicTransportError?

    suspend fun acceptStreamDataBlocked(
        packet: QUICPacket,
        streamId: Long,
        maximumStreamData: Long,
    ): QuicTransportError?

    suspend fun acceptStreamsBlockedBidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QuicTransportError?

    suspend fun acceptStreamsBlockedUnidirectional(
        packet: QUICPacket,
        maximumStreams: Long,
    ): QuicTransportError?

    suspend fun acceptNewConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
        retirePriorTo: Long,
        connectionID: ConnectionID,
        statelessResetToken: ByteArray?,
    ): QuicTransportError?

    suspend fun acceptRetireConnectionId(
        packet: QUICPacket,
        sequenceNumber: Long,
    ): QuicTransportError?

    suspend fun acceptPathChallenge(
        packet: QUICPacket,
        data: ByteArray,
    ): QuicTransportError?

    suspend fun acceptPathResponse(
        packet: QUICPacket,
        data: ByteArray,
    ): QuicTransportError?

    suspend fun acceptConnectionCloseWithTransportError(
        packet: QUICPacket,
        errorCode: QuicTransportError,
        frameType: FrameType,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QuicTransportError?

    suspend fun acceptConnectionCloseWithAppError(
        packet: QUICPacket,
        errorCode: AppError,
        reasonPhrase: ByteArray, // todo remove allocation?
    ): QuicTransportError?

    suspend fun acceptHandshakeDone(packet: QUICPacket): QuicTransportError?
}
