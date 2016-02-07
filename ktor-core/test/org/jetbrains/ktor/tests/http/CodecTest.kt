package org.jetbrains.ktor.tests.http

import org.jetbrains.ktor.http.*
import org.junit.*
import kotlin.test.*

class CodecTest {

    private val swissAndGerman = "\u0047\u0072\u00fc\u0065\u007a\u0069\u005f\u007a\u00e4\u006d\u00e4"
    private val russian = "\u0412\u0441\u0435\u043c\u005f\u043f\u0440\u0438\u0432\u0435\u0442"

    @Test
    fun testUTFEncodeDecode() {
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", encodeURLQueryComponent(russian))
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", encodeURLPart(russian))

        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", encodeURLQueryComponent(swissAndGerman))
        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", encodeURLPart(swissAndGerman))

        for (msg in listOf(russian, swissAndGerman)) {
            encodeAndDecodeTest(msg)
            encodeAndDecodeTestURLPart(msg)
        }
    }

    @Test
    fun testBasicEncodeDecode() {
        val s = "Test me!"
        val encoded = encodeURLQueryComponent(s)

        assertEquals("Test+me%21", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testBasicEncodeDecodeURLPart() {
        val s = "Test me!"
        val encoded = encodeURLPart(s)

        assertEquals("Test%20me%21", encoded)
        encodeAndDecodeTestURLPart(s)
    }

    @Test
    fun testAllReserved() {
        val s = "*~!@#$%^&()+{}\"\\;:`,/[]"
        val encoded = encodeURLQueryComponent(s)

        assertEquals("*%7E%21%40%23%24%25%5E%26%28%29%2B%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testAllReservedURLPart() {
        val s = "*~!@#$%^&()+{}\"\\;:`,/[]"
        val encoded = encodeURLPart(s)

        assertEquals("%2A~%21%40%23%24%25%5E%26%28%29+%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D", encoded)
        encodeAndDecodeTestURLPart(s)
    }

    private fun encodeAndDecodeTest(s: String) {
        assertEquals(s, decodeURLQueryComponent(encodeURLQueryComponent(s)))
    }

    private fun encodeAndDecodeTestURLPart(s: String) {
        assertEquals(s, decodeURLPart(encodeURLPart(s)))
    }

}
