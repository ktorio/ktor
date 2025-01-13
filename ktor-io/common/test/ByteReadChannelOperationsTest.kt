/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.writeString
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class ByteReadChannelOperationsTest {

    @Test
    fun testReadPacketBig() = runTest {
        val channel1 = ByteChannel()
        launch {
            channel1.writeByteArray(ByteArray(8192))
            channel1.writeByteArray(ByteArray(8192))
            channel1.flush()
        }
        val packet1 = channel1.readPacket(8192 * 2)
        assertEquals(8192 * 2, packet1.remaining)
        packet1.close()
    }

    @Test
    fun testReadRemaining() = runTest {
        val packet1 = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }
        val channel1 = ByteChannel()
        channel1.writePacket(packet1)
        channel1.flushAndClose()
        val first1 = channel1.readRemaining()
        assertEquals(12, first1.remaining)
        first1.close()
        val second1 = channel1.readRemaining()
        assertEquals(0, second1.remaining)
    }

    @Test
    fun testReadRemainingFromCancelled() = runTest {
        val packet1 = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }
        val channel1 = ByteChannel()
        channel1.writePacket(packet1)
        channel1.flush()
        channel1.cancel()
        assertFailsWith<IOException> {
            channel1.readRemaining()
        }
    }

    @Test
    fun readFully() = runTest {
        val expected1 = ByteArray(10) { it.toByte() }
        val actual1 = ByteArray(10)
        val channel1 = ByteChannel()
        channel1.writeFully(expected1)
        channel1.flush()
        channel1.readFully(actual1)
        assertContentEquals(expected1, actual1)
        actual1.fill(0)
        channel1.writeFully(expected1, 0, 5)
        channel1.flush()
        channel1.readFully(actual1, 3, 8)
        assertContentEquals(ByteArray(3) { 0 }, actual1.copyOfRange(0, 3))
        assertContentEquals(expected1.copyOfRange(0, 5), actual1.copyOfRange(3, 8))
        assertContentEquals(ByteArray(2) { 0 }, actual1.copyOfRange(8, 10))
    }

    @Test
    fun skip() = runTest {
        val ch1 = ByteChannel()
        ch1.writeFully(byteArrayOf(1, 2, 3))
        ch1.close()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertTrue(ch1.skipIfFound(delimiter1))
        assertEquals(3, ch1.readByte())
        assertTrue(ch1.isClosedForRead)
    }

    @Test
    fun skipExact() = runTest {
        val ch1 = ByteChannel()
        ch1.writeFully(byteArrayOf(1, 2))
        ch1.close()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertTrue(ch1.skipIfFound(delimiter1))
        assertTrue(ch1.isClosedForRead)
    }

    @Test
    fun skipInvalid() = runTest {
        val ch1 = ByteChannel()
        ch1.writeFully(byteArrayOf(9, 1, 2, 3))
        ch1.close()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertFalse(ch1.skipIfFound(delimiter1))
    }

    @Test
    fun skipEndOfInput() = runTest {
        val ch1 = ByteChannel()
        ch1.writeFully(byteArrayOf(1, 2))
        ch1.close()
        val delimiter1 = ByteString(byteArrayOf(1, 2, 3))
        assertFalse(ch1.skipIfFound(delimiter1))
    }

    @Test
    fun skipDelayed() = runTest {
        val ch1 = ByteChannel()
        val writer1 = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            ch1.writeByte(2)
            ch1.writeByte(3)
            ch1.close()
        }
        ch1.writeByte(1)
        ch1.flush()
        writer1.start()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertTrue(ch1.skipIfFound(delimiter1))
        assertEquals(3, ch1.readByte())
        assertTrue(ch1.isClosedForRead)
    }

    @Test
    fun skipDelayedInvalid() = runTest {
        val ch1 = ByteChannel()
        val writer1 = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            ch1.writeByte(3)
            ch1.writeByte(2)
            ch1.flush()
        }
        ch1.writeByte(1)
        ch1.flush()
        writer1.start()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertFalse(ch1.skipIfFound(delimiter1))
        assertEquals(1, ch1.readByte())
        ch1.close()
    }

    @Test
    fun readUntilEmpty() = runTest {
        assertFailsWith<IllegalStateException> {
            "test".toByteChannel().readUntil(ByteString(), ByteChannel())
        }
    }

    @Test
    fun readUntilSingle() = runTest {
        val actual1 = ByteChannel().also { out ->
            "test some more".toByteChannel().readUntil(ByteString('o'.code.toByte()), out)
            out.close()
        }.readRemaining().readText()
        assertEquals("test s", actual1)
    }

    @Test
    fun readUntilSubstring() = runTest {
        val testString1 = "This is a test--"
        val delimiter1 = "--done"
        val input1 = (testString1 + delimiter1).toByteChannel()
        val output1 = ByteChannel().also {
            input1.readUntil(delimiter1.encodeToByteString(), it)
            it.close()
        }
        assertEquals(testString1, output1.readRemaining().readText())
    }

    @Test
    fun readUntil() = runTest {
        val testString1 = """
                There once was a stream of bytes, 
                Flowing through many nights, 
                With reading so keen, 
                It stayed ever so lean, 
                Parsing data in all sorts of lights.
        """.trimIndent()
        val delimiter1 = ", \n".encodeToByteString()
        val input1 = testString1.toByteChannel()
        for (line in testString1.lines()) {
            val expected = line.trimEnd(',', ' ')
            val expectedLength = expected.length.toLong()
            val output = ByteChannel().also { out ->
                assertEquals(expectedLength, input1.readUntil(delimiter1, out, ignoreMissing = true))
                out.flushAndClose()
            }
            val actual = output.readRemaining().readText()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun readUntilStart() = runTest {
        val input1 = "This is a test".toByteChannel()
        val actual1 = writer {
            input1.readUntil("This".encodeToByteString(), channel, limit = 10, ignoreMissing = true)
        }.channel.readRemaining().readText()
        assertEquals("", actual1)
        assertEquals(" is a test", input1.readRemaining().readText())
    }

    @Test
    fun readUntilLimit() = runTest {
        val input1 = "This is a test of the readUntil limit".toByteChannel()
        assertFailsWith<IOException> {
            input1.readUntil("abc".encodeToByteString(), ByteChannel(), limit = 10, ignoreMissing = true)
        }
    }

    @Test
    fun readUntilMissing() = runTest {
        val input1 = "It's not in here".toByteChannel()
        assertFailsWith<IOException> {
            input1.readUntil("note".encodeToByteString(), ByteChannel())
        }
    }

    @Test
    fun skipIfFound() = runTest {
        val input1 = "This is a test of the skipIfFound".toByteChannel()
        assertFalse(input1.skipIfFound("Won't find this".encodeToByteString()))
        assertTrue(input1.skipIfFound("This is a test of the ".encodeToByteString()))
        assertEquals("skipIfFound", input1.readUTF8Line())
    }

    // this test ensures we don't get stuck on awaitContent
    @OptIn(InternalAPI::class, InternalIoApi::class)
    @Test
    fun readIntWithPartialContents() = runTest(timeout = 1.seconds) {
        val channel = ByteChannel()
        channel.readBuffer.buffer.writeByte(1)
        channel.writeByte(1)
        channel.writeByte(1)
        channel.writeByte(1)
        channel.flush()
        assertEquals(16843009, channel.readInt())
    }

    @OptIn(InternalAPI::class)
    private suspend fun String.toByteChannel() = ByteChannel().also {
        it.writeBuffer.writeString(this)
        it.flushAndClose()
    }
}
