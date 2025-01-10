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

class ReadUtf8LineTest {

    @Test
    fun testReadUtf8LineWithLongLineWithLimit() = runTest {
        val lineSize1 = 1024
        val line1 = "A".repeat(lineSize1)
        val channel1 = writer {
            repeat(10) {
                channel.writeStringUtf8(line1)
            }
        }.channel
        assertFailsWith<TooLongLineException> {
            channel1.readUTF8Line(8 * 1024)
        }
    }

    @Test
    fun testNewLineAfterFlush() = runTest {
        val channel1 = writer {
            channel.writeStringUtf8("4\r")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\n2\r\n")
        }.channel
        val buffer1 = StringBuilder()
        channel1.readUTF8LineTo(buffer1, 1024)
        assertEquals("4", buffer1.toString())
        buffer1.clear()
        channel1.readUTF8LineTo(buffer1, 1024)
        assertEquals("2", buffer1.toString())
    }

    @Test
    fun testFlushBeforeNewLine() = runTest {
        val channel1 = writer {
            channel.writeStringUtf8("4")
            channel.flush()
            delay(100)
            channel.writeStringUtf8("\r\n2\r\n")
        }.channel
        val buffer1 = StringBuilder()
        channel1.readUTF8LineTo(buffer1, 1024)
        assertEquals("4", buffer1.toString())
        buffer1.clear()
        channel1.readUTF8LineTo(buffer1, 1024)
        assertEquals("2", buffer1.toString())
    }
}
