/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.io.EOFException
import kotlin.test.*

class ReadLineTest {

    private val buffer = StringBuilder()

    @Test
    fun `CR and LF in different buffers`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\n2\r\n")
        }.channel

        channel.assertLines("4", "2")
    }

    @Test
    fun `flush after CR in default mode`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("2\r\n")
        }.channel

        channel.assertLines("4\r2")
    }

    @Test
    fun `flush after CR in lenient mode`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("2\r\n")
        }.channel

        channel.assertLines("4", "2", lineEnding = LineEnding.Lenient)
    }

    @Test
    fun `flush before newline`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\r\n2\r\n")
        }.channel

        channel.assertLines("4", "2")
    }

    @Test
    fun `empty channel returns -1`() = runTest {
        val channel = writer {
            delay(100)
        }.channel

        assertEquals(-1, channel.readLineTo(buffer))
        assertTrue(buffer.isEmpty())
    }

    @Test
    fun `LF at position 0`() = runTest {
        val channel = ByteReadChannel("\nline1\n")
        channel.assertLines("", "line1")
    }

    @Test
    fun `CR at position 0 in lenient mode`() = runTest {
        val channel = ByteReadChannel("\rline1\r")
        channel.assertLines("", "line1", lineEnding = LineEnding.Lenient)
    }

    @Test
    fun `strict - channel closed before line break`() = runTest {
        val lineSize = 1024
        val line = "A".repeat(lineSize)
        val channel = writer {
            channel.writeStringUtf8(line)
            channel.flushAndClose()
        }.channel

        val exception = assertFailsWith<EOFException> {
            channel.readLineStrict()
        }
        assertEquals("Unexpected end of stream after reading 1024 characters", exception.message)
    }

    @Test
    fun `strict - line without line break`() = runTest {
        val lineSize = 1024
        val line = "A".repeat(lineSize)
        val channel = writer {
            channel.writeStringUtf8(line)
        }.channel

        val exception = assertFailsWith<EOFException> {
            channel.readLineStrict()
        }
        assertEquals("Unexpected end of stream after reading 1024 characters", exception.message)
    }

    @Test
    fun `limit - exceeding limit after several buffers`() = runTest {
        val lineSize = 1024
        val line = "A".repeat(lineSize)
        val channel = writer {
            repeat(10) { channel.writeStringUtf8(line) }
        }.channel

        assertFailsWith<TooLongLineException> {
            channel.readLineStrict(limit = 8 * 1024)
        }
    }

    @Test
    fun `limit - exact limit with LF immediately after`() = runTest {
        val channel = ByteReadChannel("12345\n")
        channel.assertLines("12345", limit = 5)
    }

    @Test
    fun `limit - exact limit with CRLF immediately after`() = runTest {
        val channel = ByteReadChannel("12345\r\n")
        channel.assertLines("12345", limit = 5)
    }

    @Test
    fun `limit - exact limit with CRLF exactly at limit`() = runTest {
        val channel = ByteReadChannel("1234\r\n5\r\n")
        channel.assertLines("1234", "5", limit = 5)
    }

    @Test
    fun `limit - exact limit with CR immediately after in lenient mode`() = runTest {
        val channel = ByteReadChannel("12345\r")
        channel.assertLines("12345", limit = 5, lineEnding = LineEnding.Lenient)
    }
    @Test
    fun `limit - exact limit with CR exactly at limit in lenient mode`() = runTest {
        val channel = ByteReadChannel("1234\r5\r")
        channel.assertLines("1234", "5", limit = 5, lineEnding = LineEnding.Lenient)
    }

    @Test
    fun `limit - exact limit with CR immediately after in default mode throws`() = runTest {
        val channel = ByteReadChannel("12345\r")

        val exception = assertFailsWith<TooLongLineException> {
            channel.readLineStrictTo(buffer, limit = 5)
        }
        assertEquals("Line exceeds limit of 5 characters", exception.message)
    }

    @Test
    fun `limit - exact limit with CRLF in the next buffer`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("12345")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\r\nmore\r\n")
        }.channel

        channel.assertLines("12345", "more", limit = 5)
    }

    @Test
    fun `limit - exact limit with CR in the next buffer lenient mode`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("12345")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\rmore\r")
        }.channel

        channel.assertLines("12345", "more", limit = 5, lineEnding = LineEnding.Lenient)
    }

    @Test
    fun `limit - exact limit with CR in the next buffer throws`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("12345")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\rmore")
        }.channel

        val exception = assertFailsWith<TooLongLineException> {
            channel.readLineStrictTo(buffer, limit = 5)
        }
        assertEquals("Line exceeds limit of 5 characters", exception.message)
    }

    @Test
    fun `limit - exceeding limit in middle of line throws`() = runTest {
        val channel = ByteReadChannel("123456789\n")

        val exception = assertFailsWith<TooLongLineException> {
            channel.readLineStrictTo(buffer, limit = 5)
        }
        assertEquals("Line exceeds limit of 5 characters", exception.message)
    }

    @Test
    fun `limit - exact limit at end of stream throws in strict mode`() = runTest {
        val channel = ByteReadChannel("12345")

        val exception = assertFailsWith<EOFException> {
            channel.readLineStrictTo(buffer, limit = 5)
        }
        assertEquals("Unexpected end of stream after reading 5 characters", exception.message)
    }

    @Test
    fun `limit - exact limit at end of stream succeeds in lenient mode`() = runTest {
        val channel = ByteReadChannel("12345")
        channel.assertLines("12345", lineEnding = LineEnding.Lenient)
    }

    @Test
    fun `limit - zero limit with immediate line ending`() = runTest {
        val channel = ByteReadChannel("\ndata\n")

        val read = channel.readLineStrictTo(buffer, limit = 0)
        assertEquals(0, read)
        assertTrue(buffer.isEmpty(), "Buffer should be empty")
    }

    @Test
    fun `limit - zero limit with data throws`() = runTest {
        val channel = ByteReadChannel("X\n")

        val exception = assertFailsWith<TooLongLineException> {
            channel.readLineStrictTo(buffer, limit = 0)
        }
        assertEquals("Line exceeds limit of 0 characters", exception.message)
    }

    @Test
    fun `different line ending modes`() = runTest {
        data class TestCase(
            val input: String,
            val lineEnding: LineEnding,
            val expectedLines: List<String>
        )

        val testCases = listOf(
            TestCase(
                input = "\nline1\r\nline2\n",
                lineEnding = LineEnding.Default,
                expectedLines = listOf("", "line1", "line2")
            ),
            // CR treated as content
            TestCase(
                input = "line1\rline2\nline3\n",
                lineEnding = LineEnding.Default,
                expectedLines = listOf("line1\rline2", "line3")
            ),
            TestCase(
                input = "line1\rline2\rline3\r",
                lineEnding = LineEnding.Lenient,
                expectedLines = listOf("line1", "line2", "line3")
            ),
            // CR at position 0
            TestCase(
                input = "\rline1\rline2\r",
                lineEnding = LineEnding.Lenient,
                expectedLines = listOf("", "line1", "line2")
            ),
            // LF at position 0 with CR+LF mode (should handle LF immediately, not search for CR)
            TestCase(
                input = "\nline1\rline2\n",
                lineEnding = LineEnding.Lenient,
                expectedLines = listOf("", "line1", "line2")
            ),
            // Mixed line endings with Any mode
            TestCase(
                input = "line1\nline2\r\nline3\rline4\n",
                lineEnding = LineEnding.Lenient,
                expectedLines = listOf("line1", "line2", "line3", "line4")
            ),
            TestCase(
                input = "\n\n",
                lineEnding = LineEnding.Default,
                expectedLines = listOf("", "")
            ),
        )

        testCases.forEachIndexed { index, testCase ->
            val channel = ByteReadChannel(testCase.input)

            val actualLines = mutableListOf<String>()
            val buffer = StringBuilder()
            while (channel.readLineStrictTo(buffer, lineEnding = testCase.lineEnding) >= 0) {
                actualLines.add(buffer.toString())
                buffer.clear()
            }

            assertContentEquals(
                testCase.expectedLines,
                actualLines,
                "Test case $index failed"
            )
        }
    }

    private suspend fun ByteReadChannel.assertLines(
        vararg expected: String,
        limit: Long = -1L,
        lineEnding: LineEnding = LineEnding.Default,
    ) {
        for (line in expected) {
            buffer.clear()
            assertEquals(
                line.length.toLong(),
                if (limit == -1L) readLineTo(buffer, lineEnding) else readLineStrictTo(buffer, limit, lineEnding),
                "Unexpected line length for line \"$line\""
            )
            assertEquals(line, buffer.toString(), "Unexpected line content")
        }
        assertTrue(exhausted(), "Not all lines were read")
    }
}
