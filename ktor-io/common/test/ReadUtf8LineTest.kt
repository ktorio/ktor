/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
class ReadUtf8LineTest {

    @Test
    fun `test reading line exceeding limit`() = runTest {
        val lineSize = 1024
        val line = "A".repeat(lineSize)
        val channel = writer {
            repeat(10) {
                channel.writeStringUtf8(line)
            }
        }.channel
        assertFailsWith<TooLongLineException> {
            channel.readUTF8Line(8 * 1024)
        }
    }

    @Test
    fun `test reading line with newline after flush`() = runTest {
        val lineEndings = listOf(
            LineEndingMode.CR + LineEndingMode.CRLF,
            LineEndingMode.CRLF,
        )

        for (lineEnding in lineEndings) {
            val channel = writer {
                channel.writeStringUtf8("4\r")
                channel.flush()
                delay(100)
                channel.writeStringUtf8("\n2\r\n")
            }.channel
            val buffer = StringBuilder()
            channel.readUTF8LineTo(buffer, 1024, lineEnding)
            assertEquals("4", buffer.toString(), "Line ending: $lineEnding")
            buffer.clear()
            channel.readUTF8LineTo(buffer, 1024, lineEnding)
            assertEquals("2", buffer.toString(), "Line ending: $lineEnding")
        }
    }

    @Test
    fun `test reading line with flush after CR when only CRLF allowed`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("2\r\n")
        }.channel
        val buffer = StringBuilder()
        channel.readUTF8LineTo(buffer, 1024, LineEndingMode.CRLF)
        assertEquals("4\r2", buffer.toString())
    }

    @Test
    fun `test reading line with flush before newline`() = runTest {
        val channel = writer {
            channel.writeStringUtf8("4")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\r\n2\r\n")
        }.channel
        val buffer = StringBuilder()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("4", buffer.toString())
        buffer.clear()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("2", buffer.toString())
    }

    @Test
    fun `test reading large amount of lines completes in a reasonable time`() = runTest(timeout = 5.seconds) {
        var count = 0
        val numberOfLines = 200_000
        val channel = writer {
            repeat(numberOfLines) { i ->
                channel.writeStringUtf8("line $i\n")
            }
        }.channel

        val out = StringBuilder()
        while (channel.readUTF8LineTo(out) && count < numberOfLines) {
            count++
        }

        assertEquals(numberOfLines, count)
        assertEquals(2_088_890, out.length)
    }

    @Test
    fun `test reading long lines completes in a reasonable time`() = runTest(timeout = 5.seconds) {
        var count = 0
        val numberOfLines = 200
        val lineSize = 1024 * 1024 // 1 MB
        val line = "A".repeat(lineSize) + "\n"

        val channel = writer {
            repeat(numberOfLines) {
                channel.writeStringUtf8(line)
            }
        }.channel

        var actualLine = channel.readUTF8Line()
        while (actualLine != null) {
            count++
            assertEquals(lineSize, actualLine.length)
            actualLine = channel.readUTF8Line()
        }

        assertEquals(numberOfLines, count)
    }

    @Test
    fun `test reading lines with different line ending modes`() = runTest {
        data class TestCase(
            val input: String,
            val lineEnding: LineEndingMode,
            val expectedLines: List<String>
        )

        val testCases = listOf(
            // LF only
            TestCase(
                input = "line1\nline2\nline3\n",
                lineEnding = LineEndingMode.LF,
                expectedLines = listOf("line1", "line2", "line3")
            ),
            // CR treated as content when only LF allowed
            TestCase(
                input = "line1\rline2\nline3\n",
                lineEnding = LineEndingMode.LF,
                expectedLines = listOf("line1\rline2", "line3")
            ),
            // LF at position 0
            TestCase(
                input = "\nline1\nline2\n",
                lineEnding = LineEndingMode.LF,
                expectedLines = listOf("", "line1", "line2")
            ),
            // CR only
            TestCase(
                input = "line1\rline2\rline3\r",
                lineEnding = LineEndingMode.CR,
                expectedLines = listOf("line1", "line2", "line3")
            ),
            // LF treated as content when only CR allowed
            TestCase(
                input = "line1\nline2\rline3\r",
                lineEnding = LineEndingMode.CR,
                expectedLines = listOf("line1\nline2", "line3")
            ),
            // CR at position 0
            TestCase(
                input = "\rline1\rline2\r",
                lineEnding = LineEndingMode.CR,
                expectedLines = listOf("", "line1", "line2")
            ),
            // CR and LF both allowed but not CRLF (CR before LF should be treated as delimiter)
            TestCase(
                input = "line1\r\nline2\n",
                lineEnding = LineEndingMode.CR + LineEndingMode.LF,
                expectedLines = listOf("line1", "", "line2")
            ),
            // LF at position 0 with CR+LF mode (should handle LF immediately, not search for CR)
            TestCase(
                input = "\nline1\rline2",
                lineEnding = LineEndingMode.CR + LineEndingMode.LF,
                expectedLines = listOf("", "line1", "line2")
            ),
            // CRLF only
            TestCase(
                input = "line1\r\nline2\r\nline3\r\n",
                lineEnding = LineEndingMode.CRLF,
                expectedLines = listOf("line1", "line2", "line3")
            ),
            // Both CR and LF treated as content when only CRLF allowed
            TestCase(
                input = "line1\rline2\nline3\r\nline4\r\n",
                lineEnding = LineEndingMode.CRLF,
                expectedLines = listOf("line1\rline2\nline3", "line4")
            ),
            // Mixed line endings with Any mode
            TestCase(
                input = "line1\nline2\r\nline3\rline4\n",
                lineEnding = LineEndingMode.Any,
                expectedLines = listOf("line1", "line2", "line3", "line4")
            ),
            // Edge cases
            TestCase(
                input = "\n\n",
                lineEnding = LineEndingMode.Any,
                expectedLines = listOf("", "")
            ),
            TestCase(
                input = "no newline",
                lineEnding = LineEndingMode.Any,
                expectedLines = listOf("no newline")
            ),
        )

        testCases.forEachIndexed { index, testCase ->
            val channel = ByteChannel()
            channel.writeStringUtf8(testCase.input)
            channel.close()

            val actualLines = mutableListOf<String>()
            val buffer = StringBuilder()
            while (channel.readUTF8LineTo(buffer, lineEnding = testCase.lineEnding)) {
                actualLines.add(buffer.toString())
                buffer.clear()
            }

            assertContentEquals(
                testCase.expectedLines,
                actualLines,
                "Test case $index failed. Expected: ${testCase.expectedLines}, actual: $actualLines"
            )
        }
    }
}
