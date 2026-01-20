/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.io.EOFException
import org.junit.jupiter.api.assertThrows
import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class ByteReadChannelOperationsJvmTest {

    @Test
    fun testReadAvailableBlockFromEmpty() {
        val channel = ByteChannel()
        assertEquals(-1, channel.readAvailable { _ -> 0 })
    }

    @Test
    fun testReadAvailableBlockFromClosed() {
        val channel = ByteChannel()
        channel.close()
        assertEquals(-1, channel.readAvailable { _ -> 0 })
    }

    @Test
    fun testReadAvailableBlockAfterRead() = runBlocking {
        val channel = ByteChannel()
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })
        channel.writeFully(byteArrayOf(1, 2, 3))
        channel.flush()
        assertEquals(3, channel.readAvailable { buffer -> buffer.remaining() })
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })

        channel.close()
        assertEquals(-1, channel.readAvailable { buffer -> buffer.remaining() })
    }

    @Test
    fun testReadShortIsCooperative() = runBlocking {
        val theAnswerAsBytes: ByteArray = byteArrayOf(0, 42)
        val channel = ByteChannel()
        launch {
            for (byte in theAnswerAsBytes) {
                delay(10)
                channel.writeByte(byte)
                channel.flush()
            }
        }
        assertEquals(42, channel.readShort())
    }

    @Test
    fun testReadIntIsCooperative() = runBlocking {
        val theAnswerAsBytes: ByteArray = byteArrayOf(0, 0, 0, 42)
        val channel = ByteChannel()
        launch {
            for (byte in theAnswerAsBytes) {
                delay(10)
                channel.writeByte(byte)
                channel.flush()
            }
        }
        assertEquals(42, channel.readInt())
    }

    @Test
    fun testReadLongIsCooperative() = runBlocking {
        val theAnswerAsBytes: ByteArray = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 42)
        val channel = ByteChannel()
        launch {
            for (byte in theAnswerAsBytes) {
                delay(10)
                channel.writeByte(byte)
                channel.flush()
            }
        }
        assertEquals(42, channel.readLong())
    }

    @Test
    fun readWithGreaterMinThrows() = runTest {
        val channel = ByteChannel()
        channel.writeByte(1)
        channel.close()
        assertThrows<EOFException> {
            channel.read(2) {
                fail("There is only one byte in the channel")
            }
        }
    }

    @Test
    fun readFullyFromEmptyThrows() = runTest {
        val channel = ByteChannel()
        channel.close()
        assertThrows<EOFException> {
            channel.readFully(ByteBuffer.allocate(1))
        }
    }

    @Test
    fun readFullyFromSmallerThanOutputThrows() = runTest {
        val channel = ByteChannel()
        channel.writeByte(1)
        channel.close()
        assertThrows<EOFException> {
            channel.readFully(ByteBuffer.allocate(2))
        }
    }
}
