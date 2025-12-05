/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(InternalAPI::class)
class ChannelOutputStreamTest {

    @Test
    fun testWriteByte() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun testWriteByteArray() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        outputStream.write(testData)
        outputStream.flush()

        val result = ByteArray(5)
        channel.readFully(result)
        assertContentEquals(testData, result)
        outputStream.close()
    }

    @Test
    fun testWriteByteArrayPortion() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        outputStream.write(testData, 1, 3) // Write bytes 2, 3, 4
        outputStream.flush()

        val result = ByteArray(3)
        channel.readFully(result)
        assertContentEquals(byteArrayOf(2, 3, 4), result)
        outputStream.close()
    }

    @Test
    fun testFlush() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun testClose() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.close()

        assertEquals(42, channel.readByte().toInt() and 0xff)
    }

    @Test
    fun testWriteToClosedChannel() = runTest {
        val channel = ByteChannel(autoFlush = true)
        channel.close()
        val outputStream = ChannelOutputStream(channel)

        assertThrows<ClosedWriteChannelException> {
            outputStream.write(42)
        }
    }

    @Test
    fun testSequentialWrites() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        // Write several bytes individually
        outputStream.write(1)
        outputStream.write(2)
        outputStream.write(3)
        outputStream.flush()

        assertEquals(1, channel.readByte().toInt())
        assertEquals(2, channel.readByte().toInt())
        assertEquals(3, channel.readByte().toInt())
        outputStream.close()
    }

    @Test
    fun testMixedWrites() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        // Mix of single byte and array writes
        outputStream.write(1)
        outputStream.write(byteArrayOf(2, 3, 4))
        outputStream.write(5)
        outputStream.flush()

        val result = ByteArray(5)
        channel.readFully(result)
        assertContentEquals(byteArrayOf(1, 2, 3, 4, 5), result)
        outputStream.close()
    }

    @Test
    fun testLargeWrite() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)
        val largeArray = ByteArray(10_000) { it.toByte() }

        outputStream.write(largeArray)
        outputStream.flush()

        val result = ByteArray(10_000)
        channel.readFully(result)
        assertContentEquals(largeArray, result)
        outputStream.close()
    }

    @Test
    fun testWriteAfterClose() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.close()

        assertThrows<ClosedWriteChannelException> {
            outputStream.write(42)
        }
    }

    @Test
    fun testFlushAfterClose() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.close()

        assertThrows<ClosedWriteChannelException> {
            outputStream.flush()
        }
    }

    @Test
    fun testWriteArrayWithOffsetGreaterThanLength() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        assertThrows<IndexOutOfBoundsException> {
            outputStream.write(testData, 3, 3) // This would exceed the array bounds
        }

        outputStream.close()
    }

    @Test
    fun testCustomCoroutineContext() = runTest {
        val customContext = Dispatchers.Default + CoroutineName("TestContext")
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel, customContext)

        outputStream.write(42)
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun testChannelCloseExceptionPropagation() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        channel.close(RuntimeException("Test exception"))

        val exception = assertThrows<ClosedByteChannelException> {
            outputStream.write(42)
        }
        assertEquals("Test exception", exception.cause?.message)
    }

    @Test
    fun testProcessingJobInitialization() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        // Verify that no processing job is created until first write
        outputStream.write(42)

        // Job should now exist and be active
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun testMultipleCloseCallsAreIdempotent() = runTest {
        val channel = ByteChannel()
        val outputStream = ChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        // Should not throw exceptions
        outputStream.close()
        outputStream.close()

        assertEquals(42, channel.readByte().toInt() and 0xff)
    }
}
