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
        val channel = ByteChannel()
        launch {
            channel.writeByteArray(ByteArray(8192))
            channel.writeByteArray(ByteArray(8192))
            channel.flush()
        }
        val packet = channel.readPacket(8192 * 2)
        assertEquals(8192 * 2, packet.remaining)
        packet.close()
    }

    @Test
    fun testReadRemaining() = runTest {
        val packet = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }
        val channel = ByteChannel()
        channel.writePacket(packet)
        channel.flushAndClose()
        val first = channel.readRemaining()
        assertEquals(12, first.remaining)
        first.close()
        val second = channel.readRemaining()
        assertEquals(0, second.remaining)
    }

    @Test
    fun testReadRemainingFromCancelled() = runTest {
        val packet = buildPacket {
            writeInt(1)
            writeInt(2)
            writeInt(3)
        }
        val channel = ByteChannel()
        channel.writePacket(packet)
        channel.flush()
        channel.cancel()
        assertFailsWith<IOException> {
            channel.readRemaining()
        }
    }

    @Test
    fun readFully() = runTest {
        val expected = ByteArray(10) { it.toByte() }
        val actual = ByteArray(10)
        val channel = ByteChannel()
        channel.writeFully(expected)
        channel.flush()
        channel.readFully(actual)
        assertContentEquals(expected, actual)
        actual.fill(0)
        channel.writeFully(expected, 0, 5)
        channel.flush()
        channel.readFully(actual, 3, 8)
        assertContentEquals(ByteArray(3) { 0 }, actual.copyOfRange(0, 3))
        assertContentEquals(expected.copyOfRange(0, 5), actual.copyOfRange(3, 8))
        assertContentEquals(ByteArray(2) { 0 }, actual.copyOfRange(8, 10))
    }

    @Test
    fun skip() = runTest {
        val channel = ByteChannel()
        channel.writeFully(byteArrayOf(1, 2, 3))
        channel.close()
        val delimiter = ByteString(byteArrayOf(1, 2))
        assertTrue(channel.skipIfFound(delimiter))
        assertEquals(3, channel.readByte())
        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun skipExact() = runTest {
        val channel = ByteChannel()
        channel.writeFully(byteArrayOf(1, 2))
        channel.close()
        val delimiter = ByteString(byteArrayOf(1, 2))
        assertTrue(channel.skipIfFound(delimiter))
        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun skipInvalid() = runTest {
        val channel = ByteChannel()
        channel.writeFully(byteArrayOf(9, 1, 2, 3))
        channel.close()
        val delimiter = ByteString(byteArrayOf(1, 2))
        assertFalse(channel.skipIfFound(delimiter))
    }

    @Test
    fun skipEndOfInput() = runTest {
        val channel = ByteChannel()
        channel.writeFully(byteArrayOf(1, 2))
        channel.close()
        val delimiter = ByteString(byteArrayOf(1, 2, 3))
        assertFalse(channel.skipIfFound(delimiter))
    }

    @Test
    fun skipDelayed() = runTest {
        val channel = ByteChannel()
        val writer = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            channel.writeByte(2)
            channel.writeByte(3)
            channel.close()
        }
        channel.writeByte(1)
        channel.flush()
        writer.start()
        val delimiter1 = ByteString(byteArrayOf(1, 2))
        assertTrue(channel.skipIfFound(delimiter1))
        assertEquals(3, channel.readByte())
        assertTrue(channel.isClosedForRead)
    }

    @Test
    fun skipDelayedInvalid() = runTest {
        val channel = ByteChannel()
        val writer = launch(CoroutineName("writer"), start = CoroutineStart.LAZY) {
            channel.writeByte(3)
            channel.writeByte(2)
            channel.flush()
        }
        channel.writeByte(1)
        channel.flush()
        writer.start()
        val delimiter = ByteString(byteArrayOf(1, 2))
        assertFalse(channel.skipIfFound(delimiter))
        assertEquals(1, channel.readByte())
        channel.close()
    }

    @Test
    fun readUntilEmpty() = runTest {
        assertFailsWith<IllegalStateException> {
            "test".toByteChannel().readUntil(ByteString(), ByteChannel())
        }
    }

    @Test
    fun readUntilSingle() = runTest {
        val actual = ByteChannel().also { out ->
            "test some more".toByteChannel().readUntil(ByteString('o'.code.toByte()), out)
            out.close()
        }.readRemaining().readText()
        assertEquals("test s", actual)
    }

    @Test
    fun readUntilSubstring() = runTest {
        val testString = "This is a test--"
        val delimiter = "--done"
        val input = (testString + delimiter).toByteChannel()
        val output = ByteChannel().also {
            input.readUntil(delimiter.encodeToByteString(), it)
            it.close()
        }
        assertEquals(testString, output.readRemaining().readText())
    }

    @Test
    fun readUntil() = runTest {
        val testString = """
                There once was a stream of bytes, 
                Flowing through many nights, 
                With reading so keen, 
                It stayed ever so lean, 
                Parsing data in all sorts of lights.
        """.trimIndent()
        val delimiter = ", \n".encodeToByteString()
        val input = testString.toByteChannel()
        for (line in testString.lines()) {
            val expected = line.trimEnd(',', ' ')
            val expectedLength = expected.length.toLong()
            val output = ByteChannel().also { out ->
                assertEquals(expectedLength, input.readUntil(delimiter, out, ignoreMissing = true))
                out.flushAndClose()
            }
            val actual = output.readRemaining().readText()
            assertEquals(expected, actual)
        }
    }

    @Test
    fun readUntilStart() = runTest {
        val input = "This is a test".toByteChannel()
        val actual = writer {
            input.readUntil("This".encodeToByteString(), channel, limit = 10, ignoreMissing = true)
        }.channel.readRemaining().readText()
        assertEquals("", actual)
        assertEquals(" is a test", input.readRemaining().readText())
    }

    @Test
    fun readUntilLimit() = runTest {
        val input = "This is a test of the readUntil limit".toByteChannel()
        assertFailsWith<IOException> {
            input.readUntil("abc".encodeToByteString(), ByteChannel(), limit = 10, ignoreMissing = true)
        }
    }

    @Test
    fun readUntilMissing() = runTest {
        val input = "It's not in here".toByteChannel()
        assertFailsWith<IOException> {
            input.readUntil("note".encodeToByteString(), ByteChannel())
        }
    }

    @Test
    fun skipIfFound() = runTest {
        val input = "This is a test of the skipIfFound".toByteChannel()
        assertFalse(input.skipIfFound("Won't find this".encodeToByteString()))
        assertTrue(input.skipIfFound("This is a test of the ".encodeToByteString()))
        assertEquals("skipIfFound", input.readUTF8Line())
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
