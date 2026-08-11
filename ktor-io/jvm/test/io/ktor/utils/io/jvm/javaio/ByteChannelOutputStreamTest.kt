/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.test.dispatcher.runTestWithRealTime
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

@OptIn(InternalAPI::class)
class ByteByteChannelOutputStreamTest {

    @Test
    fun `write byte`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun `write byte array`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        outputStream.write(testData)
        outputStream.flush()

        val result = ByteArray(5)
        channel.readFully(result)
        assertContentEquals(testData, result)
        outputStream.close()
    }

    @Test
    fun `write byte array portion`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        outputStream.write(testData, 1, 3) // Write bytes 2, 3, 4
        outputStream.flush()

        val result = ByteArray(3)
        channel.readFully(result)
        assertContentEquals(byteArrayOf(2, 3, 4), result)
        outputStream.close()
    }

    @Test
    fun flush() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        assertEquals(42, channel.readByte().toInt() and 0xff)
        outputStream.close()
    }

    @Test
    fun close() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.close()

        assertEquals(42, channel.readByte().toInt() and 0xff)
    }

    @Test
    fun `sequential writes`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

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
    fun `mixed writes`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

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
    fun `large write`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)
        val largeArray = ByteArray(10_000) { it.toByte() }

        outputStream.write(largeArray)
        outputStream.flush()

        val result = ByteArray(10_000)
        channel.readFully(result)
        assertContentEquals(largeArray, result)
        outputStream.close()
    }

    @Test
    fun `write after close`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.close()

        assertThrows<ClosedWriteChannelException> {
            outputStream.write(42)
        }
    }

    @Test
    fun `flush after close`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.close()

        assertThrows<ClosedWriteChannelException> {
            outputStream.flush()
        }
    }

    @Test
    fun `write array with offset greater than length`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)
        val testData = byteArrayOf(1, 2, 3, 4, 5)

        assertThrows<IndexOutOfBoundsException> {
            outputStream.write(testData, 3, 3) // This would exceed the array bounds
        }

        outputStream.close()
    }

    @Test
    fun `channel close exception propagation`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        channel.close(RuntimeException("Test exception"))

        val exception = assertThrows<ClosedByteChannelException> {
            outputStream.write(42)
        }
        assertEquals("Test exception", exception.cause?.message)
    }

    @Test
    fun `multiple close calls are idempotent`() = runTest {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)

        outputStream.write(42)
        outputStream.flush()

        // Should not throw exceptions
        outputStream.close()
        outputStream.close()

        assertEquals(42, channel.readByte().toInt() and 0xff)
    }

    @Test
    fun `writes are streamed without explicit flush`() = runTestWithRealTime {
        val channel = ByteChannel()
        val outputStream = ByteChannelOutputStream(channel)
        val data = ByteArray(2 * 1024 * 1024) { it.toByte() }

        // Exceeds the internal flush threshold, so it must reach the channel
        // even though neither flush() nor close() is called
        launch(Dispatchers.IO) {
            outputStream.write(data)
        }

        val result = ByteArray(data.size)
        channel.readFully(result)
        assertContentEquals(data, result)
        outputStream.close()
    }
}
