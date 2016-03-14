package org.jetbrains.ktor.tests

import org.jetbrains.ktor.nio.*
import org.junit.*
import java.io.*
import java.nio.*
import java.util.zip.*
import kotlin.test.*

class DeflaterByteChannelTest {

    @Test
    fun testWithRealFile() {
        val file = listOf(File("test/org/jetbrains/ktor/tests/DeflaterByteChannelTest.kt"),
                File("ktor-core/test/org/jetbrains/ktor/tests/DeflaterByteChannelTest.kt")).first { it.exists() }

        val actual = file.asyncReadOnlyFileChannel().deflated().asInputStream().ungzip().reader().readText()
        assertEquals(file.readText(), actual)
    }

    @Test
    fun testFileChannel() {
        val file = listOf(File("test/org/jetbrains/ktor/tests/DeflaterByteChannelTest.kt"),
                File("ktor-core/test/org/jetbrains/ktor/tests/DeflaterByteChannelTest.kt")).first { it.exists() }

        val content = file.readText()

        fun read(from: Long, to: Long) =
            file.asyncReadOnlyFileChannel(from, to).asInputStream().reader().readText()

        assertEquals(content.take(3), read(0, 2))
        assertEquals(content.drop(1).take(2), read(1, 2))
        assertEquals(content.takeLast(3), read(file.length() - 3, file.length() - 1))
    }

    @Test
    fun testSmallPieces() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals(text, asyncOf(text, 3).asInputStream().reader().readText())

        for (step in 1..text.length) {
            assertEquals(text, asyncOf(text, step).deflated().asInputStream().ungzip().reader().readText())
        }
    }

    @Test
    fun testBiggerThan8k() {
        val text = buildString {
            while (length < 65536) {
                append("The quick brown fox jumps over the lazy dog")
            }
        }
        val bb = ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1))

        for (step in generateSequence(1) { it * 2 }.dropWhile { it < 64 }.takeWhile { it <= 8192 }.flatMap { sequenceOf(it, it - 1, it + 1) }) {
            bb.clear()
            assertEquals(text, asyncOf(bb, step).deflated().asInputStream().ungzip().reader().readText())
        }
    }

    private fun asyncOf(text: String, step: Int) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int) = ByteArrayAsyncReadChannel(bb, step)

    private fun InputStream.ungzip() = GZIPInputStream(this)
}