/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTime

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

    @OptIn(InternalAPI::class)
    @Test
    fun readUTF8LineTo() = runBlocking {
        var lineNumber = 0
        var count = 0
        val numberOfLines = 200_000
        val channel = writer(Dispatchers.IO) {
            for (line in generateSequence { "line ${lineNumber++}\n" }.take(numberOfLines))
                channel.writeStringUtf8(line)
        }.channel
        val out = StringBuilder()
        val time = measureTime {
            while (channel.readUTF8LineTo(out) && count < numberOfLines)
                count++
        }

        assertEquals(numberOfLines, count)
        assertTrue(time < 5.seconds, "Expected I/O to be complete in a reasonable time, but it took $time")
        assertEquals(2_088_890, out.length)
    }
}
