/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.charsets

import org.junit.Test
import java.nio.*
import kotlin.test.*

class UTFTest {
    @Test
    fun testDecodeUTF8LineOnBufferWithOffset() {
        val buffer = "HELLO\nWORLD\r\n1\r\n2\n".allocateBufferWithOffset(4)
        println("Has array: ${buffer.hasArray()}")

        buffer.assertEqualsDecodedUTF8Line("HELLO", 6)
        buffer.assertEqualsDecodedUTF8Line("WORLD", 13)
        buffer.assertEqualsDecodedUTF8Line("1", 16)
        buffer.assertEqualsDecodedUTF8Line("2", 18)
    }

    private fun ByteBuffer.assertEqualsDecodedUTF8Line(expectedResult: String, expectedPosition: Int) {
        val out = CharArray(expectedResult.length + 64)
        decodeUTF8Line(out)

        assertEquals(expectedResult, String(out).substring(0, expectedResult.length))
        assertEquals(expectedPosition, position())
    }

    private fun String.allocateBufferWithOffset(offset: Int): ByteBuffer {
        val buffer: ByteBuffer = ByteBuffer.allocate(offset + length)
        repeat(offset) { buffer.put(0) }

        val result = buffer.slice()
        toByteArray().forEach { buffer.put(it) }

        return result
    }
}
