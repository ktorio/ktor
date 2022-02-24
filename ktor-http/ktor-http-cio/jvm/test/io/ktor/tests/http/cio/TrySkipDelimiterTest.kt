/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import io.ktor.http.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import java.nio.*
import kotlin.test.*

@OptIn(ExperimentalCoroutinesApi::class)
class TrySkipDelimiterTest {
    private val ch = ByteChannel()

    @Test
    fun testSmoke(): Unit = runBlockingTest {
        ch.writeFully(byteArrayOf(1, 2, 3))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        assertTrue(ch.skipDelimiterOrEof(delimiter))
        assertEquals(3, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testSmokeWithOffsetShift(): Unit = runBlockingTest {
        ch.writeFully(byteArrayOf(9, 1, 2, 3))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        ch.discard(1)
        assertTrue(ch.skipDelimiterOrEof(delimiter))
        assertEquals(3, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testEmpty(): Unit = runBlockingTest {
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        assertFalse(ch.skipDelimiterOrEof(delimiter))
    }

    @Test
    fun testFull(): Unit = runBlockingTest {
        ch.writeFully(byteArrayOf(1, 2))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))
        assertTrue(ch.skipDelimiterOrEof(delimiter))
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testIncomplete(): Unit = runBlockingTest {
        ch.writeFully(byteArrayOf(1, 2))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2, 3))
        assertFails {
            ch.skipDelimiterOrEof(delimiter)
        }
    }

    @Test
    fun testOtherBytes(): Unit = runBlockingTest {
        ch.writeFully(byteArrayOf(7, 8))
        ch.close()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))

        assertFails {
            ch.skipDelimiterOrEof(delimiter)
        }

        // content shouldn't be consumed
        assertEquals(7, ch.readByte())
        assertEquals(8, ch.readByte())
        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testTimeSplit(): Unit = runBlockingTest {
        val writer = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            ch.writeByte(2)
            ch.close()
        }

        ch.writeByte(1)
        ch.flush()
        writer.start()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))

        assertTrue(ch.skipDelimiterOrEof(delimiter))

        assertTrue(ch.isClosedForRead)
    }

    @Test
    fun testTimeSplitNonClosed(): Unit = runBlockingTest {
        val writer = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            ch.writeByte(2)
            ch.flush()
        }

        ch.writeByte(1)
        ch.flush()
        writer.start()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))

        assertTrue(ch.skipDelimiterOrEof(delimiter))
        assertFalse(ch.isClosedForRead)
        ch.cancel()
    }

    @Test
    fun testTimeSplitWrongBytes(): Unit = runBlockingTest {
        val writer = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            ch.writeByte(33)
            ch.flush()
        }

        ch.writeByte(1)
        ch.flush()
        writer.start()

        val delimiter = ByteBuffer.wrap(byteArrayOf(1, 2))

        assertFails {
            ch.skipDelimiterOrEof(delimiter)
        }

        assertEquals(2, ch.availableForRead)
    }

    @Test
    fun testSkipTooLongDelimiter(): Unit = runBlockingTest {
        assertFails {
            ch.skipDelimiterOrEof(ByteBuffer.allocate(DEFAULT_BUFFER_SIZE * 2))
        }
    }
}
