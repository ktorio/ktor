/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * See [ReadLineTest] for `readLineTo`/`readLineStrictTo` tests.
 * These tests check only that [readUTF8LineTo] behavior is preserved after introduction of new functions.
 */
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
        val channel = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\n2\r\n")
        }.channel
        val buffer = StringBuilder()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("4", buffer.toString())
        buffer.clear()
        channel.readUTF8LineTo(buffer, 1024)
        assertEquals("2", buffer.toString())
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
    fun `test read line without line ending`() = runTest {
        val lineSize = 42
        val line = "A".repeat(lineSize)
        val channel = writer {
            channel.writeStringUtf8(line)
        }.channel

        val buffer = StringBuilder()
        assertTrue(channel.readUTF8LineTo(buffer))
        assertEquals(line, buffer.toString())
    }
}
