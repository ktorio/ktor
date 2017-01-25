package org.jetbrains.ktor.tests.nio

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.cio.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class AsyncSeekAndCutTest {
    @Test
    fun testCutOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("Hello, async!", RangeReadChannel(source(), 0L, 1000).readText())
        assertEquals("Hel", RangeReadChannel(source(), 0L, 3).readText())
        assertEquals("", RangeReadChannel(source(), 0L, 0).readText())
    }

    @Test
    fun testEmptySource() {
        val source = { asyncOf("") }

        assertEquals("", RangeReadChannel(source(), 0L, 1000).readText())
//        assertEquals("", RangeReadChannel(source(), 10, 0).readText())
        assertEquals("", RangeReadChannel(source(), 0L, 0).readText())
    }

    @Test
    fun testSeekOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ello, async!", RangeReadChannel(source(), 1L, 1000).readText())
        assertEquals("", RangeReadChannel(source(), 13L, 1000).readText())
    }

    @Test
    fun testSeekAndCut() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ell", RangeReadChannel(source(), 1L, 3).readText())
        assertEquals("", RangeReadChannel(source(), 13L, 0).readText())
    }

    @Test
    fun testSeekForSeek() {
        val source = { asyncOf("Hello, async!") }

        assertEquals(", ", RangeReadChannel(RangeReadChannel(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testSeekForSeekBytePerByte() {
        val source = { asyncOf("Hello, async!", 1) }

        assertEquals(", ", RangeReadChannel(RangeReadChannel(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testMultipleSeekForSource() = runBlocking(Here) {
        // TODO move to separate test
        val source = { asyncOf("Hello, async!") }

        val s1 = source()
        s1.seek(3)

        assertEquals("lo, async!", s1.readText())

        val s2 = asyncOf("Hello, async!")
        s2.seek(10L)
        s2.seek(4)

        assertEquals("o, async!", s2.readText())
    }

    private fun ReadChannel.readText() = toInputStream().reader().readText()
    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteBufferReadChannel(bb, step)
}