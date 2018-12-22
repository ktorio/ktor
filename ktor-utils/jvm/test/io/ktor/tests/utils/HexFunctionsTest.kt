package io.ktor.tests.utils

import io.ktor.util.*
import org.junit.Assert.*
import kotlin.test.*

class HexFunctionsTest {
    @Test
    fun testEmpty() {
        assertEquals("", hex(ByteArray(0)))
        assertArrayEquals(ByteArray(0), hex(""))
    }

    @Test
    fun testSingle() {
        test(byteArrayOf(1), "01")
    }

    @Test
    fun testMultiple() {
        test(byteArrayOf(1, 2, 3, 4), "01020304")
    }

    @Test
    fun testHexDigits() {
        test(byteArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0x0a, 0x0b, 0x0c, 0x0d, 0x0e, 0x0f),
            "000102030405060708090a0b0c0d0e0f")
    }

    @Test
    fun testSignedAndBorders() {
        test(byteArrayOf(-1), "ff")
        test(byteArrayOf(15), "0f")
        test(byteArrayOf(16), "10")
        test(byteArrayOf(-16), "f0")
    }

    private fun test(byteArray: ByteArray, text: String) {
        assertEquals(text, hex(byteArray))
        assertArrayEquals(byteArray, hex(text))
    }
}
