package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.nio.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class AsyncSeekAndCutTest {
    @Test
    fun testCutOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("Hello, async!", AsyncSeekAndCut(source(), 0L, 1000).readText())
        assertEquals("Hel", AsyncSeekAndCut(source(), 0L, 3).readText())
        assertEquals("", AsyncSeekAndCut(source(), 0L, 0).readText())
    }

    @Test
    fun testEmptySource() {
        val source = { asyncOf("") }

        assertEquals("", AsyncSeekAndCut(source(), 0L, 1000).readText())
//        assertEquals("", AsyncSeekAndCut(source(), 10, 0).readText())
        assertEquals("", AsyncSeekAndCut(source(), 0L, 0).readText())
    }

    @Test
    fun testSeekOnly() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ello, async!", AsyncSeekAndCut(source(), 1L, 1000).readText())
        assertEquals("", AsyncSeekAndCut(source(), 13L, 1000).readText())
    }

    @Test
    fun testSeekAndCut() {
        val source = { asyncOf("Hello, async!") }

        assertEquals("ell", AsyncSeekAndCut(source(), 1L, 3).readText())
        assertEquals("", AsyncSeekAndCut(source(), 13L, 0).readText())
    }

    @Test
    fun testSeekForSeek() {
        val source = { asyncOf("Hello, async!") }

        assertEquals(", ", AsyncSeekAndCut(AsyncSeekAndCut(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testSeekForSeekBytePerByte() {
        val source = { asyncOf("Hello, async!", 1) }

        assertEquals(", ", AsyncSeekAndCut(AsyncSeekAndCut(source(), 4L, 5L), 1L, 2L).readText())
    }

    @Test
    fun testMultipleSeekForSource() {
        // TODO move to separate test
        val source = { asyncOf("Hello, async!") }

        val s1 = source()
        s1.seek(3, EmptyHandler)

        assertEquals("lo, async!", s1.readText())

        val s2 = asyncOf("Hello, async!")
        s2.seek(10L, EmptyHandler)
        s2.seek(4, EmptyHandler)

        assertEquals("o, async!", s2.readText())
    }

    private fun AsyncReadChannel.readText() = asInputStream().reader().readText()
    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteArrayAsyncReadChannel(bb, step)

    private object EmptyHandler : AsyncHandler {
        override fun success(count: Int) {
        }

        override fun successEnd() {
        }

        override fun failed(cause: Throwable) {
            throw AssertionError(cause)
        }
    }
}