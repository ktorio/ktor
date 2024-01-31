/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.recovery

import io.ktor.network.quic.errors.AppError
import io.ktor.network.quic.frames.*

internal interface Retransmission {
    suspend fun retransmitCrypto()

    suspend fun retransmitStream(streamId: Long, offset: Long?, specifyLength: Boolean, fin: Boolean, data: ByteArray)

    suspend fun retransmitAck()

    suspend fun retransmitResetStream(streamId: Long, applicationProtocolErrorCode: AppError, finalSize: Long)

    suspend fun retransmitStopSending(streamId: Long, applicationProtocolErrorCode: AppError)

    // Section 10, RFC-9000
//    fun retransmitConnectionClose()

    suspend fun retransmitMaxData()

    suspend fun retransmitMaxStreamData(streamId: Long)

    suspend fun retransmitMaxStreamsBidirectional()

    suspend fun retransmitMaxStreamsUnidirectional()

    suspend fun retransmitDataBlocked()

    suspend fun retransmitStreamDataBlocked()

    suspend fun retransmitStreamsBlocked()

    suspend fun retransmitPathChallenge()

    suspend fun retransmitNewConnectionID()

    suspend fun retransmitRetireConnectionID()

    suspend fun retransmitNewToken()

    suspend fun retransmitHandshakeDone()
}

internal class RetransmissionImpl(private val connection: ConnectionForRetransmission) : Retransmission {
    private lateinit var frameWriter: FrameWriter

    fun withFrameWriter(frameWriter: FrameWriter) {
        this.frameWriter = frameWriter
    }

    override suspend fun retransmitCrypto() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitStream(
        streamId: Long,
        offset: Long?,
        specifyLength: Boolean,
        fin: Boolean,
        data: ByteArray,
    ) {
        if (connection.streamManager.streamCancelled(streamId)) {
            return
        }

        frameWriter.writeStream(streamId, offset, specifyLength, fin, data)
    }

    override suspend fun retransmitAck() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitResetStream(
        streamId: Long,
        applicationProtocolErrorCode: AppError,
        finalSize: Long,
    ) {
        if (connection.streamManager.streamFinished(streamId)) {
            return
        }

        frameWriter.writeResetStream(streamId, applicationProtocolErrorCode, finalSize)
    }

    override suspend fun retransmitStopSending(streamId: Long, applicationProtocolErrorCode: AppError) {
        if (connection.streamManager.streamFinished(streamId)) {
            return
        }

        frameWriter.writeStopSending(streamId, applicationProtocolErrorCode)
    }

    override suspend fun retransmitMaxData() {
        if (!connection.needToRetransmitMaxData()) {
            return
        }

        frameWriter.writeMaxData(connection.currentMaxData())
    }

    override suspend fun retransmitMaxStreamData(streamId: Long) {
        if (!connection.streamManager.needToRetransmitMaxStreamData(streamId)) {
            return
        }

        frameWriter.writeMaxStreamData(streamId, connection.streamManager.currentMaxStreamData(streamId))
    }

    override suspend fun retransmitMaxStreamsBidirectional() {
        if (!connection.needToRetransmitMaxStreamsBidirectional()) {
            return
        }

        frameWriter.writeMaxStreamsBidirectional(connection.currentMaxStreamsBidirectional())
    }

    override suspend fun retransmitMaxStreamsUnidirectional() {
        if (!connection.needToRetransmitMaxStreamsUnidirectional()) {
            return
        }

        frameWriter.writeMaxStreamsUnidirectional(connection.currentMaxStreamsUnidirectional())
    }

    override suspend fun retransmitDataBlocked() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitStreamDataBlocked() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitStreamsBlocked() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitPathChallenge() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitNewConnectionID() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitRetireConnectionID() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitNewToken() {
        TODO("Not yet implemented")
    }

    override suspend fun retransmitHandshakeDone() {
        TODO("Not yet implemented")
    }
}
