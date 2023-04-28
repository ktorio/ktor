package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import kotlin.test.*

class CharsetsTest {
    @Test
    fun testUtf8() {
        Charsets.forName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testISO() {
        Charsets.forName("UTF-8").newEncoder().encode("test").release()
    }

    @Test
    fun testLatin1() {
        Charsets.forName("Latin1").newEncoder().encode("test").release()
    }

    @Test
    fun testNonExisting() {
        assertFailsWith<IllegalArgumentException> {
            Charsets.forName("abracadabra-encoding")
        }
    }

    @Test
    fun testIllegal() {
        assertFailsWith<IllegalArgumentException> {
            Charsets.forName("%s")
        }
    }
}
