package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import kotlin.test.*

class CharsetsTest {
    @Test
    fun testUtf8() {
        charsetForName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testISO() {
        charsetForName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testLatin1() {
        charsetForName("Latin1").newEncoder().encode("test").release()
    }

    @Test
    fun testNonExisting() {
        assertFailsWith<IllegalArgumentException> {
            charsetForName("abracadabra-encoding")
        }
    }

    @Test
    fun testIllegal() {
        assertFailsWith<IllegalArgumentException> {
            charsetForName("%s")
        }
    }
}
