/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.charsets

import java.nio.*
import kotlin.test.*

class UTFTest {
    @Test
    fun testDecodeUTF8LineOnBufferWithOffset() {
        val buffer = "HELLO\nWORLD\r\n1\r\n2\n".allocateBufferWithOffset(offset = 4)

        buffer.assertEqualsDecodedUTF8Line("HELLO", expectedPosition = 6)
        buffer.assertEqualsDecodedUTF8Line("WORLD", expectedPosition = 13)
        buffer.assertEqualsDecodedUTF8Line("1", expectedPosition = 16)
        buffer.assertEqualsDecodedUTF8Line("2", expectedPosition = 18)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun testDecodeUTF8LineOnBufferWithOffsetAndStringWithoutNewLineAtTheEnd() {
        val buffer = "HELLO WORLD".allocateBufferWithOffset(offset = 4)

        buffer.assertEqualsDecodedUTF8Line("HELLO WORLD", expectedPosition = 11)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun testDecodeUTF8LineOnBufferWithOffsetAndEmptyString() {
        val buffer = "".allocateBufferWithOffset(offset = 4)

        buffer.assertEqualsDecodedUTF8Line("", expectedPosition = 0)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun testDecodeUTF8LineOnBufferWithOffsetAnd2ByteChars() {
        val buffer = "ПРИВЕТ\nМИР\r\n1\r\n2\n".allocateBufferWithOffset(offset = 4)

        buffer.assertEqualsDecodedUTF8Line("ПРИВЕТ", expectedPosition = 13)
        buffer.assertEqualsDecodedUTF8Line("МИР", expectedPosition = 21)
        buffer.assertEqualsDecodedUTF8Line("1", expectedPosition = 24)
        buffer.assertEqualsDecodedUTF8Line("2", expectedPosition = 26)
        assertEquals(0, buffer.remaining())
    }

    @Test
    fun testDecodeUTF8LineOnBufferWithOffsetAndStringWithoutNewLineAtTheEndAnd2ByteChars() {
        val buffer = "ПРИВЕТМИР".allocateBufferWithOffset(offset = 4)

        buffer.assertEqualsDecodedUTF8Line("ПРИВЕТМИР", expectedPosition = 18)
        assertEquals(0, buffer.remaining())
    }

    private fun ByteBuffer.assertEqualsDecodedUTF8Line(expectedResult: String, expectedPosition: Int) {
        val out = CharArray(expectedResult.length + 64)
        decodeUTF8Line(out)

        assertEquals(expectedResult, String(out).substring(0, expectedResult.length))
        assertEquals(expectedPosition, position())
    }

    private fun String.allocateBufferWithOffset(offset: Int): ByteBuffer {
        val array = toByteArray()
        val buffer: ByteBuffer = ByteBuffer.allocate(offset + array.size)
        repeat(offset) { buffer.put(0) }

        val result = buffer.slice()
        array.forEach { buffer.put(it) }

        return result
    }
}
