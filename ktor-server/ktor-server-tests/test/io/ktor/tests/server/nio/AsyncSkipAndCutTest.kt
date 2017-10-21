package io.ktor.tests.server.nio

import io.ktor.cio.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class AsyncSkipAndCutTest {
    fun rangeChannel(source: ReadChannel, skip: Long, maxSize: Long): ReadChannel {
        // Hide RandomAccess capability of source channel
        return RangeReadChannel(CompositeReadChannel(sequenceOf({ source })), skip, maxSize)
    }

    @Test
    fun testCutOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("Hello, async!", rangeChannel(source(), 0L, 1000).readText())
        assertEquals("Hel", rangeChannel(source(), 0L, 3).readText())
        assertEquals("", rangeChannel(source(), 0L, 0).readText())
    }

    @Test
    fun testEmptySource() {
        val source = { asyncOf("") }

        assertEquals("", rangeChannel(source(), 0L, 1000).readText())
        assertEquals("", rangeChannel(source(), 10, 0).readText())
        assertEquals("", rangeChannel(source(), 0L, 0).readText())
    }

    @Test
    fun testSkipOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ello, async!", rangeChannel(source(), 1L, 1000).readText())
        assertEquals("", rangeChannel(source(), 13L, 1000).readText())
    }

    @Test
    fun testSkipAndCut() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ell", rangeChannel(source(), 1L, 3).readText())
        assertEquals("", rangeChannel(source(), 13L, 0).readText())
    }

    @Test
    fun testSkipForSkip() {
        val source = { asyncOf("Hello, async!") }

        assertEquals(", ", rangeChannel(rangeChannel(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testSkipForSkipBytePerByte() {
        val source = { asyncOf("Hello, async!", 1) }

        assertEquals(", ", rangeChannel(rangeChannel(source(), 4L, 5L), 1L, 2L).readText())
    }

    private fun ReadChannel.readText() = toInputStream().reader().readText()
    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteBufferReadChannel(bb, step)
}