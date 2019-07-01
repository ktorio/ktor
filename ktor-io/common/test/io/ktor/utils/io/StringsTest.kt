package io.ktor.utils.io

import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import kotlin.test.*
import kotlin.test.Test

open class StringsTest : ByteChannelTestBase(true) {
    private val channel: ByteChannel get() = ch

    @Test
    fun testReadString() = runTest {
        writeString("Hello, World!")
        channel.close()
        assertEquals("Hello, World!", channel.readUTF8Line())
    }

    @Test
    fun testReadLines1() {
        testReadLine("\r", "", "")
    }

    @Test
    fun testReadLinesCases() {
        testReadLine("abc", "abc", "")
        testReadLine("", null, "")

        testReadLine("\n", "", "")
        testReadLine("\r", "", "")
        testReadLine("\r\n", "", "")
        testReadLine("1\n", "1", "")
        testReadLine("1\r", "1", "")
        testReadLine("1\r\n", "1", "")

        testReadLine("\n2", "", "2")
        testReadLine("\r2", "", "2")
        testReadLine("\r\n2", "", "2")
        testReadLine("1\n2", "1", "2")
        testReadLine("1\r2", "1", "2")
        testReadLine("1\r\n2", "1", "2")

        // unicode
        testReadLine("\u0440\n", "\u0440", "")
        testReadLine("\u0440\n1", "\u0440", "1")
        testReadLine("\u0440\r", "\u0440", "")
        testReadLine("\u0440\r2", "\u0440", "2")
    }

    private fun testReadLine(source: String, expectedLine: String?, expectedRemaining: String) {
        val content = buildPacket {
            append(source)
        }.readBytes()

        // no splitting
        runTest {
            ByteReadChannel(content)
        }

        // split
        for (splitAt in 0 until content.size) {
            val ch = ByteChannel(true)
            runTest {
                launch {
                    ch.writeFully(content, 0, splitAt)
                    yield()
                    ch.writeFully(content, splitAt, content.size - splitAt)
                    ch.close()
                }

                testReadLine(ch, expectedLine, expectedRemaining)
            }
        }
    }

    private suspend fun testReadLine(ch: ByteReadChannel, expectedLine: String?, expectedRemaining: String) {
        val line = ch.readUTF8Line()
        assertEquals(expectedLine, line, "readLine result comparison failed.")

        val packet = ch.readRemaining()

        try {
            if (expectedRemaining.isNotEmpty()) {
                assertNotEquals(0, packet.remaining, "Unexpected EOF. Expected >= 0")
            }

            assertEquals(expectedRemaining,
                    Charsets.UTF_8.newDecoder().decode(packet),
                    "Remaining bytes after readLine comparison failed.")
        } finally {
            packet.release()
        }
    }

    @Test
    fun testReadLines() = runTest {
        writeString("Hello, World!\nLine2")
        assertEquals("Hello, World!", channel.readUTF8Line())
        channel.close()
        assertEquals("Line2", channel.readUTF8Line())
    }

    @Test
    fun testReadLinesCrLf() = runTest {
        writeString("Hello, World!\r\nLine2")
        assertEquals("Hello, World!", channel.readUTF8Line())
        channel.close()
        assertEquals("Line2", channel.readUTF8Line())
    }

    @Test
    fun testReadASCIILineLf() {
        runTest {
            writeParts("A", "B\n", "C")

            assertEquals("AB", channel.readUTF8Line())
            assertEquals("C", channel.readUTF8Line())
            assertEquals(null, channel.readUTF8Line())
        }
    }

    @Test
    fun testReadASCIILineCrLf() {
        runTest {
            writeParts("A", "B\r\n", "C")

            assertEquals("AB", channel.readUTF8Line())
            assertEquals("C", channel.readUTF8Line())
            assertEquals(null, channel.readUTF8Line())
        }
    }

    @Test
    fun testReadASCIILineCrLfBadSplit() {
        runTest {
            writeParts("A", "B\r", "\nC")

            assertEquals("AB", channel.readUTF8Line())
            assertEquals("C", channel.readUTF8Line())
            assertEquals(null, channel.readUTF8Line())
        }
    }

    @Test
    fun testReadASCIILineTrailingLf() {
        runTest {
            writeParts("A", "B\n", "C\n")

            assertEquals("AB", channel.readUTF8Line())
            assertEquals("C", channel.readUTF8Line())
            assertEquals(null, channel.readUTF8Line())
        }
    }

    @Test
    fun testReadASCIILineLeadingLf() {
        runTest {
            writeParts("\nA", "B\n", "C")

            assertEquals("", channel.readUTF8Line())
            assertEquals("AB", channel.readUTF8Line())
            assertEquals("C", channel.readUTF8Line())
            assertEquals(null, channel.readUTF8Line())
        }
    }

//    @Test
//    fun testLookAhead() {
//        val text = buildString() {
//            for (i in 0 until 65535) {
//                append((i and 0xf).toString(16))
//            }
//        }.toByteArray()
//
//        runBlocking {
//            launch(CommonPool) {
//                channel.writeFully(text)
//                channel.close()
//            }
//
//            val comparison = ByteBuffer.wrap(text)
//
//            val arr = ByteArray(128)
//            var rem = text.size
//            val rnd = Random()
//
//            while (rem > 0) {
//                val s = rnd.nextInt(arr.size).coerceIn(1, rem)
//                arr.fill(0)
//                val rc = channel.readAvailable(arr, 0, s)
//
//                if (rc == -1) fail("EOF")
//
//                val actual = String(arr, 0, rc)
//
//                val expectedBytes = ByteArray(rc)
//                comparison.get(expectedBytes)
//                val expected = expectedBytes.toString(Charsets.ISO_8859_1)
//
//                assertEquals(expected, actual)
//
//                rem -= rc
//            }
//        }
//    }
//
//    @Test
//    fun testLongLinesConcurrent() {
//        val lines = (0..1024).map { size ->
//            buildString(size) {
//                for (i in 0 until size) {
//                    append((i and 0xf).toString(16))
//                }
//            }
//        }
//
//        runBlocking {
//            launch(CommonPool) {
//                for (part in lines) {
//                    writeString(part + "\n")
//                }
//                channel.close()
//            }
//
//            for (expected in lines) {
//                assertEquals(expected, channel.readASCIILine(expected.length))
//            }
//
//            assertNull(channel.readASCIILine())
//        }
//    }

    @Test
    fun testLongLinesSequential() {
        val lines = (0..1024).map { size ->
            buildString(size) {
                for (i in 0 until size) {
                    append((i and 0xf).toString(16))
                }
            }
        }

        runTest {
            launch {
                for (part in lines) {
                    writeString(part + "\n")
                    yield()
                }
                channel.close()
            }

            for (expected in lines) {
                yield()
                assertEquals(expected, channel.readUTF8Line(expected.length))
            }

            assertNull(channel.readUTF8Line())
        }
    }

    @Test
    fun testReadUTF8Line2bytes() {
        val parts = byteArrayOf(0xd0.toByte(), 0x9a.toByte(), 0x0a)

        runTest {
            channel.writeFully(parts)
            assertEquals("\u041a", channel.readUTF8Line())
        }
    }

    @Test
    fun testReadUTF8Line3bytes() {
        val parts = byteArrayOf(0xe0.toByte(), 0xaf.toByte(), 0xb5.toByte(), 0x0a)

        runTest {
            channel.writeFully(parts)
            assertEquals("\u0BF5", channel.readUTF8Line())
        }
    }

    @Test
    fun testReadUTF8Line4bytes() {
        val parts = byteArrayOf(0xF0.toByte(), 0xA6.toByte(), 0x88.toByte(), 0x98.toByte(), 0x0a)

        runTest {
            channel.writeFully(parts)
            assertEquals("\uD858\uDE18", channel.readUTF8Line())
        }
    }

    private suspend fun writeString(s: String) {
        channel.writeStringUtf8(s)
    }

    private fun writeParts(vararg parts: String) {
        launch {
            parts.forEach { p ->
                writeString(p)
                yield()
            }

            channel.close()
        }
    }
}