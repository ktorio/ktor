package io.ktor.utils.io.tests

import io.ktor.utils.io.charsets.*
import kotlin.test.*

class CharsetsTest {
    @Test
    fun testUtf8() {
        Charset.forName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testISO() {
        Charset.forName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testLatin1() {
        Charset.forName("Latin1").newEncoder().encode("test").release()
    }

    @Test
    fun testNonExisting() {
        try {
            Charset.forName("abracadabra-encoding")
            fail("abracadabra-encoding is not supported so should fail")
        } catch (expected: IllegalArgumentException) {
        }
    }
}
