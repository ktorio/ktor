/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.EOFException
import kotlinx.io.IOException
import kotlin.test.*

class ByteChannelTest {

    @Test
    fun `write after close should fail`() = runTest {
        val channel = ByteChannel()
        channel.close()
        assertFailsWith<IOException> {
            channel.writeByte(1)
        }
    }

    @Test
    fun testReadFromEmpty() = runTest {
        val channel1 = ByteChannel()
        channel1.flushAndClose()
        assertFailsWith<EOFException> {
            channel1.readByte()
        }
    }

    @Test
    fun testWriteReadByte() = runTest {
        val channel1 = ByteChannel()
        channel1.writeByte(42)
        channel1.flushAndClose()
        assertEquals(42, channel1.readByte())
    }

    @Test
    fun testCancel() = runTest {
        val channel1 = ByteChannel()
        channel1.cancel()
        assertFailsWith<IOException> {
            channel1.readByte()
        }
    }

    @Test
    fun testWriteInClosedChannel() = runTest {
        val channel1 = ByteChannel()
        channel1.flushAndClose()
        assertTrue(channel1.isClosedForWrite)
        assertFailsWith<ClosedWriteChannelException> {
            channel1.writeByte(42)
        }
    }

    @Test
    fun testCreateFromArray() = runTest {
        val array1 = byteArrayOf(1, 2, 3, 4, 5)
        val channel1 = ByteReadChannel(array1)
        val result1 = channel1.toByteArray()
        assertTrue(array1.contentEquals(result1))
    }

    @Test
    fun testChannelFromString() = runTest {
        val string1 = "Hello, world!"
        val channel1 = ByteReadChannel(string1)
        val result1 = channel1.readRemaining().readText()
        assertEquals(string1, result1)
    }

    @Test
    fun testCancelByteReadChannel() = runTest {
        val channel1 = ByteReadChannel(byteArrayOf(1, 2, 3, 4, 5))
        channel1.cancel()
        assertFailsWith<IOException> {
            channel1.readByte()
        }
    }

    @Test
    fun testCloseAfterAwait() = runTest {
        val channel1 = ByteChannel()
        val job1 = launch(start = CoroutineStart.UNDISPATCHED) {
            channel1.awaitContent()
        }
        channel1.flushAndClose()
        job1.join()
    }

    @Test
    fun testChannelMaxSize() = runTest {
        val channel1 = ByteChannel()
        val job1 = launch(Dispatchers.Unconfined) {
            channel1.writeFully(ByteArray(CHANNEL_MAX_SIZE))
        }
        delay(100)
        assertFalse(job1.isCompleted)
        channel1.readByte()
        job1.join()
    }

    @Test
    fun testChannelMaxSizeWithException() = runTest {
        val channel1 = ByteChannel()
        var writerThrows1 = false
        val deferred1 = async(Dispatchers.Unconfined) {
            try {
                channel1.writeFully(ByteArray(CHANNEL_MAX_SIZE))
            } catch (cause: IOException) {
                writerThrows1 = true
            }
        }
        assertFalse(deferred1.isCompleted)
        channel1.cancel()
        deferred1.await()
        assertTrue(writerThrows1)
    }

    @Test
    fun testIsCloseForReadAfterCancel() = runTest {
        val packet1 = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }
        val channel1 = ByteChannel()
        channel1.writePacket(packet1)
        channel1.flush()
        channel1.cancel()
        assertTrue(channel1.isClosedForRead)
    }

    @Test
    fun testWriteAndFlushResumeReader() = runTest {
        val channel1 = ByteChannel()
        val reader1 = async {
            channel1.readByte()
        }
        yield()
        channel1.writeByte(42)
        channel1.flush()
        assertEquals(42, reader1.await())
    }
}
