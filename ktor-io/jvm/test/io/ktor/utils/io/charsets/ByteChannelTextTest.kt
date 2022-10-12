/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*

class ByteChannelTextTest {

    @Test
    fun testReadUtf8LineThrowTooLongLine() = runBlocking<Unit> {
        val line100 = (0..99).joinToString("")
        val channel = ByteChannel()
        channel.writeStringUtf8(line100)
        channel.close()

        assertFailsWith<TooLongLineException> {
            channel.readUTF8Line(50)
        }
    }

    @Test
    fun testReadUtf8Line32k() = runBlocking {
        val line = "x".repeat(32 * 1024)
        val bytes = line.encodeToByteArray()
        val channel = ByteReadChannel(bytes)

        val result = channel.readUTF8Line()
        assertEquals(line, result)
    }

    @Test
    fun testReadLineUtf8Chunks() = runBlocking {
        val line = "x".repeat(32 * 1024)
        val channel = writer {
            channel.writeStringUtf8(line)
        }.channel

        val result = channel.readUTF8Line()
        assertEquals(line, result)
    }

    @Test
    fun test2EmptyLines() {
        val text = ByteReadChannel("\r\n\r\n")

        runBlocking {
            assertEquals(4, text.availableForRead)
            assertEquals("", text.readUTF8Line())
            assertEquals(2, text.availableForRead)
            assertEquals(2, text.totalBytesRead)
            assertEquals("", text.readUTF8Line())
            assertNull(text.readUTF8Line())
        }
    }
}
