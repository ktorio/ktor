/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("LocalVariableName")

package io.ktor.network.quic.frames

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.errors.*
import io.ktor.utils.io.core.*


/**
 * [RFC Reference](https://www.rfc-editor.org/rfc/rfc9000.html#name-frame-types-and-formats)
 */
internal object FrameWriter {
    fun writePadding(
        packetBuilder: BytePacketBuilder
    ) = with(packetBuilder) {
        writeType(FrameType_v1.PADDING)
    }

    fun writePing(
        packetBuilder: BytePacketBuilder
    ) = with(packetBuilder) {
        writeType(FrameType_v1.PING)
    }

    fun writeACK(
        packetBuilder: BytePacketBuilder,
        largestAcknowledged: VarInt,
        ackRangeCount: VarInt,
        firstACKRange: VarInt,
        ackRangesGaps: Array<VarInt>,
        ackRangesCounts: Array<VarInt>,
    ) = packetBuilder.writeACK(
        typeV1 = FrameType_v1.ACK,
        largestAcknowledged = largestAcknowledged,
        ackRangeCount = ackRangeCount,
        firstACKRange = firstACKRange,
        ackRangesGaps = ackRangesGaps,
        ackRangesCounts = ackRangesCounts,
        ect0 = null,
        ect1 = null,
        ectCE = null
    )

    fun writeACKWithECN(
        packetBuilder: BytePacketBuilder,
        largestAcknowledged: VarInt,
        ackRangeCount: VarInt,
        firstACKRange: VarInt,
        ackRangesGaps: Array<VarInt>,
        ackRangesCounts: Array<VarInt>,
        ect0: VarInt,
        ect1: VarInt,
        ectCE: VarInt,
    ) = packetBuilder.writeACK(
        typeV1 = FrameType_v1.ACK_ECN,
        largestAcknowledged = largestAcknowledged,
        ackRangeCount = ackRangeCount,
        firstACKRange = firstACKRange,
        ackRangesGaps = ackRangesGaps,
        ackRangesCounts = ackRangesCounts,
        ect0 = ect0,
        ect1 = ect1,
        ectCE = ectCE
    )

    private fun BytePacketBuilder.writeACK(
        typeV1: FrameType_v1,
        largestAcknowledged: VarInt,
        ackRangeCount: VarInt,
        firstACKRange: VarInt,
        ackRangesGaps: Array<VarInt>,
        ackRangesCounts: Array<VarInt>,
        ect0: VarInt?,
        ect1: VarInt?,
        ectCE: VarInt?,
    ) {
        require(ackRangesGaps.size == ackRangesCounts.size) {
            "Array of ACK Ranges gaps should be the same size as the corresponding ACK counts array"
        }

        writeType(typeV1)
        writeVarInt(largestAcknowledged)
        writeVarInt(ackRangeCount)
        writeVarInt(firstACKRange)

        ackRangesGaps.forEachIndexed { i, gap ->
            writeVarInt(gap)
            writeVarInt(ackRangesCounts[i])
        }

        ect0?.let {
            writeVarInt(ect0)
            writeVarInt(ect1!!)
            writeVarInt(ectCE!!)
        }
    }

    fun writeResetStream(
        packetBuilder: BytePacketBuilder,
        streamId: VarInt,
        applicationProtocolErrorCode: VarInt,
        finaSize: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.RESET_STREAM)
        writeVarInt(streamId)
        writeVarInt(applicationProtocolErrorCode)
        writeVarInt(finaSize)
    }

    fun writeStopSending(
        packetBuilder: BytePacketBuilder,
        streamId: VarInt,
        applicationProtocolErrorCode: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.STOP_SENDING)
        writeVarInt(streamId)
        writeVarInt(applicationProtocolErrorCode)
    }

    fun writeCrypto(
        packetBuilder: BytePacketBuilder,
        offset: VarInt,
        data: ByteArray,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.CRYPTO)
        writeVarInt(offset)
        writeVarInt(data.size.toVarInt()) // todo check that should size in bytes
        writeFully(data)
    }

    fun writeNewToken(
        packetBuilder: BytePacketBuilder,
        token: ByteArray,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.NEW_TOKEN)
        writeVarInt(token.size.toVarInt())
        writeFully(token)
    }

    fun writeStream(
        packetBuilder: BytePacketBuilder,
        streamId: VarInt,
        offset: VarInt?,
        length: VarInt?,
        fin: Boolean,
        data: ByteArray,
    ) = with(packetBuilder) {
        val type = when {
            offset == null && length == null && !fin -> FrameType_v1.STREAM
            offset == null && length == null && fin -> FrameType_v1.STREAM_FIN
            offset == null && length != null && !fin -> FrameType_v1.STREAM_LEN
            offset == null && length != null && fin -> FrameType_v1.STREAM_LEN_FIN
            offset != null && length == null && !fin -> FrameType_v1.STREAM_OFF
            offset != null && length == null && fin -> FrameType_v1.STREAM_OFF_FIN
            offset != null && length != null && !fin -> FrameType_v1.STREAM_OFF_LEN
            offset != null && length != null && fin -> FrameType_v1.STREAM_OFF_LEN_FIN
            else -> error("unreachable")
        }

        writeType(type)
        writeVarInt(streamId)
        offset?.let { writeVarInt(it) }
        length?.let { writeVarInt(it) }
        writeFully(data)
    }

    fun writeMaxData(
        packetBuilder: BytePacketBuilder,
        maximumData: VarInt
    ) = with(packetBuilder) {
        writeType(FrameType_v1.MAX_DATA)
        writeVarInt(maximumData)
    }

    fun writeMaxStreamData(
        packetBuilder: BytePacketBuilder,
        streamId: VarInt,
        maximumStreamData: VarInt
    ) = with(packetBuilder) {
        writeType(FrameType_v1.MAX_STREAM_DATA)
        writeVarInt(streamId)
        writeVarInt(maximumStreamData)
    }

    fun writeMaxStreamsBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.MAX_STREAMS_BIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    fun writeMaxStreamsUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.MAX_STREAMS_UNIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    fun writeDataBlocked(
        packetBuilder: BytePacketBuilder,
        maximumData: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.DATA_BLOCKED)
        writeVarInt(maximumData)
    }

    fun writeStreamDataBlocked(
        packetBuilder: BytePacketBuilder,
        streamId: VarInt,
        maximumStreamData: VarInt
    ) = with(packetBuilder) {
        writeType(FrameType_v1.STREAM_DATA_BLOCKED)
        writeVarInt(streamId)
        writeVarInt(maximumStreamData)
    }

    fun writeStreamsBlockedBidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.STREAMS_BLOCKED_BIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    fun writeStreamsBlockedUnidirectional(
        packetBuilder: BytePacketBuilder,
        maximumStreams: VarInt,
    ) = with(packetBuilder) {
        writeType(FrameType_v1.STREAMS_BLOCKED_UNIDIRECTIONAL)
        writeVarInt(maximumStreams)
    }

    fun writeNewConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: VarInt,
        retirePriorTo: VarInt,
        connectionId: ConnectionId_v1,
        statelessResetToken: ByteArray,
    ) = with(packetBuilder) {
        require(connectionId.array.isNotEmpty()) {
            "Length of Connection ID in NEW_CONNECTION_ID frame should be at least 1 byte"
        }
        require(statelessResetToken.size == 16) {
            "Stateless Reset Token size in NEW_CONNECTION_ID frame should be 16 bytes"
        }

        writeType(FrameType_v1.NEW_CONNECTION_ID)
        writeVarInt(sequenceNumber)
        writeVarInt(retirePriorTo)
        writeConnectionId(connectionId)
        writeFully(statelessResetToken)
    }

    fun writeRetireConnectionId(
        packetBuilder: BytePacketBuilder,
        sequenceNumber: VarInt
    ) = with(packetBuilder) {
        writeType(FrameType_v1.RETIRE_CONNECTION_ID)
        writeVarInt(sequenceNumber)
    }

    fun writePathChallenge(
        packetBuilder: BytePacketBuilder,
        data: ByteArray
    ) = with(packetBuilder) {
        require(data.size == 8) {
            "Data field size in PATH_CHALLENGE frame should be 8 bytes"
        }
        writeType(FrameType_v1.PATH_CHALLENGE)
        writeFully(data)
    }

    fun writePathResponse(
        packetBuilder: BytePacketBuilder,
        data: ByteArray
    ) = with(packetBuilder) {
        require(data.size == 8) {
            "Data field size in PATH_RESPONSE frame should be 8 bytes"
        }
        writeType(FrameType_v1.PATH_RESPONSE)
        writeFully(data)
    }

    fun writeConnectionCloseWithQUICError(
        packetBuilder: BytePacketBuilder,
        errorCode: TransportErrorCode_v1,
        frameTypeV1: FrameType_v1,
        reasonPhase: ByteArray
    ) = writeConnectionClose(
        packetBuilder = packetBuilder,
        typeV1 = FrameType_v1.CONNECTION_CLOSE_TRANSPORT_ERR,
        errorCode = errorCode,
        frameTypeV1 = frameTypeV1,
        reasonPhase = reasonPhase
    )

    fun writeConnectionCloseWithAppError(
        packetBuilder: BytePacketBuilder,
        errorCode: AppErrorCode_v1,
        reasonPhase: ByteArray
    ) = writeConnectionClose(
        packetBuilder = packetBuilder,
        typeV1 = FrameType_v1.CONNECTION_CLOSE_APP_ERR,
        errorCode = errorCode,
        frameTypeV1 = null,
        reasonPhase = reasonPhase
    )

    private fun writeConnectionClose(
        packetBuilder: BytePacketBuilder,
        typeV1: FrameType_v1,
        errorCode: ErrorCode_v1,
        frameTypeV1: FrameType_v1?,
        reasonPhase: ByteArray
    ) = with(packetBuilder) {
        writeType(typeV1)
        writeErrorCode(errorCode)
        frameTypeV1?.let { writeType(it) }
        writeVarInt(reasonPhase.size.toVarInt())
        writeFully(reasonPhase)
    }

    fun writeHandShakeDone(
        packetBuilder: BytePacketBuilder
    ) = with(packetBuilder) {
        writeType(FrameType_v1.HANDSHAKE_DONE)
    }

    private fun BytePacketBuilder.writeType(typeV1: FrameType_v1) {
        // it is actually a varint with length 8, as frame types are all values in 0x00..0x1e
        writeByte(typeV1.typeValue.toByte())
    }

    private fun BytePacketBuilder.writeConnectionId(idV1: ConnectionId_v1) {
        writeByte(idV1.array.size.toByte())
        writeFully(idV1.array)
    }

    private fun BytePacketBuilder.writeErrorCode(errorCode: ErrorCode_v1) {
        TODO("write as varint")
    }
}
