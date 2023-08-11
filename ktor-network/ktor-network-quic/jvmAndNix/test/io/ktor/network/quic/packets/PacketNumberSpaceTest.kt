/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.packets

import kotlin.test.*

class PacketNumberSpaceTest {
    @Test
    fun testPNS() {
        val pns = PacketNumberSpace()

        assertEquals(0, pns.largestPacketNumber, "Packet number")
        assertEquals(-1, pns.largestAcknowledged, "Largest acknowledged")

        assertEquals(0, pns.next(), "First packet")
        assertEquals(1, pns.next(), "Second packet")

        assertEquals(
            expected = setOf<Long>(0, 1),
            actual = pns.getUnacknowledgedLocalPacketNumbers(),
            message = "Unacknowledged local packet numbers",
        )

        pns.next()
        pns.next()

        pns.receivePacket(2)
        pns.receivePacket(3)
        pns.receivePacket(6)

        val ranges = pns.getAckRanges() ?: fail("Expected ack ranges")

        assertContentEquals(listOf(6, 6, 3, 2), ranges, "Ack ranges")

        pns.registerSentRanges(ranges, pns.next())

        pns.receivePacket(0)
        pns.receivePacket(1)
        pns.receivePacket(4)

        pns.processAcknowledgements(listOf(3, 3, 1, 1))

        assertEquals(
            expected = setOf<Long>(0, 2, 4),
            actual = pns.getUnacknowledgedLocalPacketNumbers(),
            message = "Unacknowledged local packet numbers 2"
        )

        pns.receivePacket(9)
        pns.receivePacket(10)
        pns.receivePacket(13)
        pns.receivePacket(14)
        pns.receivePacket(15)

        pns.processAcknowledgements(listOf(4, 4))

        assertEquals(4, pns.largestAcknowledged, "Largest acknowledged 2")

        val ranges2 = pns.getAckRanges() ?: fail("Expected ack ranges 2")

        assertContentEquals(listOf(15, 13, 10, 9, 4, 4, 1, 0), ranges2, "Ack ranges 2")

        pns.registerSentRanges(ranges2, pns.next())

        pns.processAcknowledgements(listOf(5, 5, 2, 2, 0, 0))

        assertTrue(
            actual = pns.getUnacknowledgedLocalPacketNumbers().isEmpty(),
            message = "Unacknowledged local packet numbers 3"
        )

        assertNull(pns.getAckRanges(), "Ack ranges 3")

        assertEquals(6, pns.largestPacketNumber, "Packet Number 2")
        assertEquals(5, pns.largestAcknowledged, "Largest acknowledged 3")
    }
}
