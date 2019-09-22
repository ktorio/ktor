package io.ktor.utils.io.tests

import io.ktor.utils.io.bits.*
import kotlin.test.*

class ReverseByteOrderTest {
    @Test
    fun testReverseShort() {
        val v: Short = 0x1234
        assertEquals(0x3412, v.reverseByteOrder())
    }

    @Test
    fun testReverseShortBig() {
        val v: Short = 0xab12.toShort()
        assertEquals(0x12ab, v.reverseByteOrder())
    }

    @Test
    fun testReverseUnsignedShort() {
        val v: UShort = 0xab12u
        assertEquals(0x12abu, v.reverseByteOrder())
    }

    @Test
    fun testReverseInt() {
        val v = 0x12345566
        assertEquals(0x66553412, v.reverseByteOrder())
    }

    @Test
    fun testReverseIntBig() {
        val v: Int = 0xf2345566.toInt()
        assertEquals(0x665534f2, v.reverseByteOrder())
    }

    @Test
    fun testReverseUnsignedInt() {
        val v: UInt = 0xf2345566u
        assertEquals(0x665534f2u, v.reverseByteOrder())
    }

    @Test
    fun testReverseLong() {
        val v: Long = 0x1234556677889922
        assertEquals(0x2299887766553412, v.reverseByteOrder())
    }

    @Test
    fun testReverseLongBig() {
        val v: Long = 0xf234556677889922u.toLong()
        assertEquals(0x22998877665534f2, v.reverseByteOrder())
    }

    @Test
    fun testReverseUnsignedLong() {
        val v: ULong = 0xf234556677889922u
        assertEquals(0x22998877665534f2u, v.reverseByteOrder())
    }

    @Test
    fun testReverseFloat() {
        val v = 1.5f
        v.reverseByteOrder()
        // TODO test?
    }

    @Test
    fun testReverseDouble() {
        val v = 1.5
        v.reverseByteOrder()
        // TODO test?
    }
}
