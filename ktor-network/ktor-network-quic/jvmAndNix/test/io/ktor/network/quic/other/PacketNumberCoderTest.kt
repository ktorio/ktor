/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.quic.other

import io.ktor.network.quic.packets.*
import io.ktor.network.quic.util.*
import kotlin.test.*

class PacketNumberCoderTest {
    @Test
    fun testDecodePacketNumber() {
        // test from RFC
        assertEquals(0xA82F9B32, decodePacketNumber(0xA82F30EA, 0x9B32u, 2u))
        assertEquals(300, decodePacketNumber(384, 44u, 1u))
        assertEquals(254, decodePacketNumber(268, 254u, 1u))
        assertEquals(522, decodePacketNumber(510, 10u, 1u))
        assertEquals(65455, decodePacketNumber(65454, 65455u, 4u))
        assertEquals(4494967300L, decodePacketNumber(4494967299, 200000004u, 4u))
    }

    @Test
    fun testGetPacketNumberLength() {
        assertEquals(1u, getPacketNumberLength(0, -1))
        assertEquals(1u, getPacketNumberLength(POW_2_07 - 1, -1))
        assertEquals(2u, getPacketNumberLength(POW_2_07, -1))

        assertEquals(2u, getPacketNumberLength(POW_2_15 - 1, -1))
        assertEquals(3u, getPacketNumberLength(POW_2_15, -1))

        assertEquals(3u, getPacketNumberLength(POW_2_23 - 1, -1))
        assertEquals(4u, getPacketNumberLength(POW_2_23, -1))
        assertEquals(4u, getPacketNumberLength(POW_2_30, -1))

        assertEquals(3u, getPacketNumberLength(POW_2_23 + 5, 15))
        assertEquals(1u, getPacketNumberLength(POW_2_23, POW_2_23 - 17))
        assertEquals(2u, getPacketNumberLength(POW_2_30, POW_2_30 - POW_2_15))
    }
}
