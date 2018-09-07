package io.ktor.tests.auth

import io.ktor.util.*
import org.junit.Test
import kotlin.test.*

class CryptoTest {
    @Test
    fun testBase64() {
        assertEquals("AAAA", encodeBase64(ByteArray(3)))
        assertEquals(ByteArray(3), decodeBase64("AAAA"))
    }

    @Test
    fun testHex() {
        assertEquals("00af", hex(byteArrayOf(0, 0xaf.toByte())))
        assertEquals(byteArrayOf(0, 0xaf.toByte()), hex("00af"))
    }

    @Test
    fun testRaw() {
        assertEquals(byteArrayOf(0x31, 0x32, 0x33), raw("123"))
    }

    private fun assertEquals(a: ByteArray, b: ByteArray) {
        fun Byte.h() = Integer.toHexString(toInt() and 0xff)
        assertEquals(a.map(Byte::h), b.map { it.h() })
    }

    private fun raw(s: String) = s.toByteArray(Charsets.UTF_8)
}
