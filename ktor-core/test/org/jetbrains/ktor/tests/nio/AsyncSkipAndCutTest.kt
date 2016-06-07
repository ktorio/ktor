package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.nio.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class AsyncSkipAndCutTest {
    @Test
    fun testCutOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("Hello, async!", AsyncSkipAndCut(source(), 0L, 1000).readText())
        assertEquals("Hel", AsyncSkipAndCut(source(), 0L, 3).readText())
        assertEquals("", AsyncSkipAndCut(source(), 0L, 0).readText())
    }

    @Test
    fun testEmptySource() {
        val source = { asyncOf("") }

        assertEquals("", AsyncSkipAndCut(source(), 0L, 1000).readText())
        assertEquals("", AsyncSkipAndCut(source(), 10, 0).readText())
        assertEquals("", AsyncSkipAndCut(source(), 0L, 0).readText())
    }

    @Test
    fun testSkipOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ello, async!", AsyncSkipAndCut(source(), 1L, 1000).readText())
        assertEquals("", AsyncSkipAndCut(source(), 13L, 1000).readText())
    }

    @Test
    fun testSkipAndCut() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ell", AsyncSkipAndCut(source(), 1L, 3).readText())
        assertEquals("", AsyncSkipAndCut(source(), 13L, 0).readText())
    }

    @Test
    fun testSkipForSkip() {
        val source = { asyncOf("Hello, async!") }

        assertEquals(", ", AsyncSkipAndCut(AsyncSkipAndCut(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testSkipForSkipBytePerByte() {
        val source = { asyncOf("Hello, async!", 1) }

        assertEquals(", ", AsyncSkipAndCut(AsyncSkipAndCut(source(), 4L, 5L), 1L, 2L).readText())
    }

    private fun AsyncReadChannel.readText() = asInputStream().reader().readText()
    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteArrayAsyncReadChannel(bb, step)
}