package io.ktor.tests.http

import io.ktor.http.*
import kotlin.test.*

class CodecTest {
    private val swissAndGerman = "\u0047\u0072\u00fc\u0065\u007a\u0069\u005f\u007a\u00e4\u006d\u00e4"
    private val russian = "\u0412\u0441\u0435\u043c\u005f\u043f\u0440\u0438\u0432\u0435\u0442"

    @Test
    fun testUTFEncodeDecode() {
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC_%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", encodeURLQueryComponent(russian))
        assertEquals("%D0%92%D1%81%D0%B5%D0%BC%5F%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", encodeURLPart(russian))

        assertEquals("Gr%C3%BCezi_z%C3%A4m%C3%A4", encodeURLQueryComponent(swissAndGerman))
        assertEquals("Gr%C3%BCezi%5Fz%C3%A4m%C3%A4", encodeURLPart(swissAndGerman))

        for (msg in listOf(russian, swissAndGerman)) {
            encodeAndDecodeTest(msg)
        }
    }

    @Test
    fun testBasicEncodeDecode() {
        val test = "Test me!"
        assertEquals("Test%20me!", encodeURLQueryComponent(test))
        assertEquals("Test%20me%21", encodeURLPart(test))
        encodeAndDecodeTest(test)
    }

    @Test
    fun testSimpleBasicEncodeDecode() {
        val s = "simple"
        val encoded = encodeURLQueryComponent(s)

        assertEquals("simple", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testBasicEncodeDecodeURLPart() {
        val s = "Test me!"
        val encoded = encodeURLPart(s)

        assertEquals("Test%20me%21", encoded)
        encodeAndDecodeTest(s)
    }

    @Test
    fun testAllReserved() {
        val text = "*~!@#$%^&()+{}\"\\;:`,/[]"
        assertEquals("*~!@#$%25%5E&()+%7B%7D%22%5C;:%60,/[]", encodeURLQueryComponent(text))
        assertEquals("%2A%7E%21%40%23%24%25%5E%26%28%29%2B%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D", encodeURLPart(text))

        encodeAndDecodeTest(text)
    }

    @Test
    fun testBrokenOrIncompleteHEX() {
        assertFails {
            decodeURLQueryComponent("foo+%+bar")
        }
        assertEquals("0", decodeURLQueryComponent("%30"))
        assertFails {
            decodeURLQueryComponent("%")
        }
        assertFails {
            decodeURLQueryComponent("%0")
        }
    }

    private fun encodeAndDecodeTest(text: String) {
        assertEquals(text, decodeURLQueryComponent(encodeURLQueryComponent(text)))
        assertEquals(text, decodeURLPart(encodeURLPart(text)))
    }
}
