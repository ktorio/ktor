package io.ktor.tests.utils

import io.ktor.cio.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.coroutines.experimental.io.jvm.javaio.*
import org.junit.Test
import java.io.*
import java.nio.ByteBuffer
import java.util.zip.*
import kotlin.test.*

class DeflaterReadChannelTest {

    @Test
    fun testWithRealFile() {
        val file = listOf(File("test/io/ktor/tests/utils/DeflaterReadChannelTest.kt"),
                File("ktor-server/ktor-server-tests/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt")).first(File::exists)

        testReadChannel(file.readText(), file.readChannel())
        testWriteChannel(file.readText(), file.readChannel())
    }

    @Test
    fun testFileChannel() {
        val file = listOf(File("test/io/ktor/tests/utils/DeflaterReadChannelTest.kt"),
                File("ktor-server/ktor-server-tests/test/io/ktor/tests/utils/DeflaterReadChannelTest.kt")).first(File::exists)

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
        assertEquals(text, asyncOf(text).toInputStream().reader().readText())

        for (step in 1..text.length) {
            testReadChannel(text, asyncOf(text))
            testWriteChannel(text, asyncOf(text))
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
            testReadChannel(text, asyncOf(bb))

            bb.clear()
            testWriteChannel(text, asyncOf(bb))
        }
    }

    @Test
    fun testLargeContent() {
        val text = buildString {
            for (i in 1..16384) {
                append("test$i\n".padStart(10, ' '))
            }
        }

        testReadChannel(text, asyncOf(text))
        testWriteChannel(text, asyncOf(text))
    }

    private fun asyncOf(text: String) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)))
    private fun asyncOf(bb: ByteBuffer) = ByteReadChannel(bb)

    private fun InputStream.ungzip() = GZIPInputStream(this)

    private fun testReadChannel(expected: String, src: ByteReadChannel) {
        assertEquals(expected, src.deflated().toInputStream().ungzip().reader().readText())
    }

    private fun testWriteChannel(expected: String, src: ByteReadChannel) {
        val channel = ByteChannel()
        launch(Unconfined) {
            src.copyAndClose((channel as ByteWriteChannel).deflated())
        }

        val result = channel.toInputStream().ungzip().reader().readText()
        assertEquals(expected, result)
    }
}