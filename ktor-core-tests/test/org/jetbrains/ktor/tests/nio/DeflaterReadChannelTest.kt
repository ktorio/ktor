package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.cio.*
import org.junit.*
import java.io.*
import java.nio.*
import java.util.zip.*
import kotlin.test.*

class DeflaterReadChannelTest {

    @Test
    fun testWithRealFile() {
        val file = listOf(File("test/org/jetbrains/ktor/tests/nio/DeflaterReadChannelTest.kt"),
                File("ktor-core-tests/test/org/jetbrains/ktor/tests/nio/DeflaterReadChannelTest.kt")).first(File::exists)

        val actual = file.readChannel().deflated().toInputStream().ungzip().reader().readText()
        assertEquals(file.readText(), actual)
    }

    @Test
    fun testFileChannel() {
        val file = listOf(File("test/org/jetbrains/ktor/tests/nio/DeflaterReadChannelTest.kt"),
                File("ktor-core-tests/test/org/jetbrains/ktor/tests/nio/DeflaterReadChannelTest.kt")).first(File::exists)

        val content = file.readText()

        fun read(from: Long, to: Long) =
                file.readChannel(from, to).toInputStream().reader().readText()

        assertEquals(content.take(3), read(0, 2))
        assertEquals(content.drop(1).take(2), read(1, 2))
        assertEquals(content.takeLast(3), read(file.length() - 3, file.length() - 1))
    }

    @Test
    fun testSmallPieces() {
        val text = "The quick brown fox jumps over the lazy dog"
        assertEquals(text, asyncOf(text, 3).toInputStream().reader().readText())

        for (step in 1..text.length) {
            assertEquals(text, asyncOf(text, step).deflated().toInputStream().ungzip().reader().readText())
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
            assertEquals(text, asyncOf(bb, step).deflated().toInputStream().ungzip().reader().readText())
        }
    }

    @Test
    fun testLargeContent() {
        val text = buildString {
            for (i in 1..16384) {
                append("test$i\n".padStart(10, ' '))
            }
        }
        assertEquals(text, asyncOf(text, 3).deflated().toInputStream().ungzip().reader().readText())
    }

    private fun asyncOf(text: String, step: Int) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int) = ByteBufferReadChannel(bb, step)

    private fun InputStream.ungzip() = GZIPInputStream(this)
}