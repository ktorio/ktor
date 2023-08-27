/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import io.ktor.network.quic.bytes.*
import io.ktor.network.quic.connections.*
import io.ktor.network.quic.consts.*
import io.ktor.network.quic.tls.*
import io.ktor.network.quic.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlin.test.*

class PacketSendHandlerTest {
    @Test
    fun initial() = handlerTest<ReadyPacketHandler.Initial>(overhead = 43) { writer, handler ->
        PacketSendHandlerImpl.Initial(writer, handler, ConnectionRole.SERVER)
    }

    @Test
    fun handshake() = handlerTest<ReadyPacketHandler>(overhead = 39) { writer, handler ->
        PacketSendHandlerImpl.Handshake(writer, handler, ConnectionRole.SERVER)
    }

    @Test
    fun oneRTT() = handlerTest<ReadyPacketHandler.OneRTT>(overhead = 25) { writer, handler ->
        PacketSendHandlerImpl.OneRTT(writer, handler, ConnectionRole.SERVER)
    }

    private suspend fun test(overhead: Int, handler: PacketSendHandlerImpl<*>) {
        val pn1 = handler.writeFrame(TEST_DATAGRAM_MAX_SIZE / 2) {
            writeFully(zeroByteArray(TEST_DATAGRAM_MAX_SIZE / 2))
        }

        assertEquals(0, pn1, "First packet, first frame")

        val nextPacketSize = TEST_DATAGRAM_MAX_SIZE - TEST_DATAGRAM_MAX_SIZE / 2 - overhead

        val pn2 = handler.writeFrame(nextPacketSize) { writeFully(zeroByteArray(nextPacketSize)) }

        assertEquals(0, pn2, "First packet, second frame")

        val pn3 = handler.writeFrame(1) { writeFully(zeroByteArray(1)) }

        assertEquals(1, pn3, "Second packet, first frame")

        val pn4 = handler.writeFrame(TEST_DATAGRAM_MAX_SIZE / 2) {
            writeFully(zeroByteArray(TEST_DATAGRAM_MAX_SIZE / 2))
        }

        assertEquals(1, pn4, "Second packet, second frame")

        handler.finish()

        val pn5 = handler.writeFrame(TEST_DATAGRAM_MAX_SIZE / 2 - 1 - overhead) {
            writeFully(zeroByteArray(TEST_DATAGRAM_MAX_SIZE / 2 - 1 - overhead))
        }

        assertEquals(2, pn5, "Third packet, first frame")

        val pn6 = handler.writeFrame(1) { writeFully(zeroByteArray(1)) }

        assertEquals(3, pn6, "Fourth packet, first frame")
    }

    private fun <H : ReadyPacketHandler> handlerTest(
        overhead: Int,
        getHandler: (PacketWriter, H) -> PacketSendHandlerImpl<H>
    ) = runBlocking {
        val buffer = BytePacketBuilder()

        @Suppress("UNCHECKED_CAST")
        val handler = getHandler(TestPacketWriter(buffer), TestReadyPacketHandler(buffer) as H)

        test(overhead, handler)
    }

    companion object {
        internal const val TEST_DATAGRAM_MAX_SIZE = 200
    }
}

private class TestPacketWriter(private val buffer: BytePacketBuilder) : PacketWriter {
    override fun writeVersionNegotiationPacket(packetBuilder: BytePacketBuilder, version: UInt32, destinationConnectionID: QUICConnectionID, sourceConnectionID: QUICConnectionID, supportedVersions: Array<UInt32>) { } // ktlint-disable max-line-length

    override fun writeRetryPacket(packetBuilder: BytePacketBuilder, originalDestinationConnectionID: QUICConnectionID, version: UInt32, destinationConnectionID: QUICConnectionID, sourceConnectionID: QUICConnectionID, retryToken: ByteArray) { } // ktlint-disable max-line-length

    override suspend fun writeInitialPacket(largestAcknowledged: Long, packetBuilder: BytePacketBuilder, version: UInt32, destinationConnectionID: QUICConnectionID, sourceConnectionID: QUICConnectionID, token: ByteArray, packetNumber: Long, payload: ByteArray) { // ktlint-disable max-line-length
        buffer.writeFully(payload)
    }

    override suspend fun writeHandshakePacket(largestAcknowledged: Long, packetBuilder: BytePacketBuilder, version: UInt32, destinationConnectionID: QUICConnectionID, sourceConnectionID: QUICConnectionID, packetNumber: Long, payload: ByteArray) { // ktlint-disable max-line-length
        buffer.writeFully(payload)
    }

    override suspend fun writeZeroRTTPacket(largestAcknowledged: Long, packetBuilder: BytePacketBuilder, version: UInt32, destinationConnectionID: QUICConnectionID, sourceConnectionID: QUICConnectionID, packetNumber: Long, payload: ByteArray) { // ktlint-disable max-line-length
        buffer.writeFully(payload)
    }

    override suspend fun writeOneRTTPacket(largestAcknowledged: Long, packetBuilder: BytePacketBuilder, spinBit: Boolean, keyPhase: Boolean, destinationConnectionID: QUICConnectionID, packetNumber: Long, payload: ByteArray) { // ktlint-disable max-line-length
        buffer.writeFully(payload)
    }
}

private class TestReadyPacketHandler(private val buffer: BytePacketBuilder) :
    ReadyPacketHandler.Initial,
    ReadyPacketHandler.OneRTT,
    ReadyPacketHandler.Retry,
    ReadyPacketHandler.VersionNegotiation {

    override val destinationConnectionID: QUICConnectionID = zeroByteArray(4).asCID()
    override val destinationConnectionIDSize: Int = 4
    override val sourceConnectionID: QUICConnectionID = zeroByteArray(4).asCID()
    override val sourceConnectionIDSize: Int = 4
    override val token: ByteArray = zeroByteArray(3)
    override val originalDestinationConnectionID: QUICConnectionID = zeroByteArray(4).asCID()
    override val retryToken: ByteArray = zeroByteArray(4)
    override val spinBit: Boolean = false
    override val keyPhase: Boolean = false
    override val supportedVersions: Array<UInt32> = arrayOf(QUICVersion.V1)

    override fun getLargestAcknowledged(encryptionLevel: EncryptionLevel): Long = 0

    var pn = 0L
    override fun getPacketNumber(encryptionLevel: EncryptionLevel): Long = pn++

    override val maxUdpPayloadSize: Int = PacketSendHandlerTest.TEST_DATAGRAM_MAX_SIZE

    override val usedDatagramSize: Int get() = buffer.size

    override suspend fun forceEndDatagram() {
        buffer.reset()
    }

    override suspend fun withDatagramBuilder(body: suspend (BytePacketBuilder) -> Unit) {
        body(buffer)
    }
}
