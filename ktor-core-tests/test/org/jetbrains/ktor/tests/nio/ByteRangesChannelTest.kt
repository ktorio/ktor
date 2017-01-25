package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.features.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class ByteRangesChannelTest {
    @Test
    fun testAscendingNoLength() {
        val source = asyncOf("0123456789abcdef")
        val ranges = ByteRangesChannel.forSeekable(listOf(1L .. 3L, 5L..6L), source, null, "boundary-1", "text/plain")

        assertEquals("""
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 1-3/*

        123
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 5-6/*

        56
        boundary-1
        """.trimIndent(), ranges.readText().replaceEndlines())
    }

    @Test
    fun testAscendingWithLength() {
        val source = asyncOf("0123456789abcdef")
        val ranges = ByteRangesChannel.forSeekable(listOf(1L .. 3L, 5L..6L), source, 99L, "boundary-1", "text/plain")

        assertEquals("""
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 1-3/99

        123
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 5-6/99

        56
        boundary-1
        """.trimIndent(), ranges.readText().replaceEndlines())
    }

    @Test
    fun testNonAscendingNoLength() {
        val source = asyncOf("0123456789abcdef")
        val ranges = ByteRangesChannel.forSeekable(listOf(1L .. 3L, 5L .. 6L, 0L .. 1L), source, null, "boundary-1", "text/plain")

        assertEquals("""
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 1-3/*

        123
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 5-6/*

        56
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 0-1/*

        01
        boundary-1
        """.trimIndent(), ranges.readText().replaceEndlines())
    }

    @Test
    fun testNonSeekable() {
        val source = RangeReadChannel(asyncOf("0123456789abcdef"), 0L, 1000)
        val ranges = ByteRangesChannel.forRegular(listOf(1L .. 3L, 5L..6L, 10L..12L), source, 99L, "boundary-1", "text/plain")

        assertEquals("""
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 1-3/99

        123
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 5-6/99

        56
        boundary-1
        Content-Type: text/plain
        Content-Range: bytes 10-12/99

        abc
        boundary-1
        """.trimIndent(), ranges.readText().replaceEndlines())
    }

    private fun String.replaceEndlines() = replace("\r\n", "\n")
    private fun ReadChannel.readText() = toInputStream().reader().readText()
    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteBufferReadChannel(bb, step)
}