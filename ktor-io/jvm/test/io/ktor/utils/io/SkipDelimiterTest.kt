/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io

import java.nio.*
import kotlin.test.*

class SkipDelimiterTest : ByteChannelTestBase() {
    @Test
    fun testSmoke(): Unit = runTest {
        ch.writeFully(byteArrayOf(1, 2, 3))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        ch.skipDelimiter(delimiter)
        assertEquals(3, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testSmokeWithOffsetShift(): Unit = runTest {
        ch.writeFully(byteArrayOf(9, 1, 2, 3))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        ch.discard(1)
        ch.skipDelimiter(delimiter)
        assertEquals(3, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testEmpty(): Unit = runTest {
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        assertFails {
            ch.skipDelimiter(delimiter)
        }
    }

    @Test
    fun testFull(): Unit = runTest {
        ch.writeFully(byteArrayOf(1, 2))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        ch.skipDelimiter(delimiter)
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testIncomplete(): Unit = runTest {
        ch.writeFully(byteArrayOf(1, 2))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        assertFails {
            ch.skipDelimiter(delimiter)
        }
    }

    @Test
    fun testOtherBytes(): Unit = runTest {
        ch.writeFully(byteArrayOf(7, 8))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))

        assertFails {
            ch.skipDelimiter(delimiter)
        }

        // content shouldn't be consumed
        assertEquals(7, ch.readByte())
        assertEquals(8, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }
}
