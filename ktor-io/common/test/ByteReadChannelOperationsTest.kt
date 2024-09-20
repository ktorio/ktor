import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.errors.*
import kotlinx.coroutines.*
import kotlinx.io.IOException
import kotlin.test.*

/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

class ByteReadChannelOperationsTest {

    @Test
    fun testReadPacketBig() = testSuspend {
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
    fun testReadRemaining() = testSuspend {
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
    fun testReadRemainingFromCancelled() = testSuspend {
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
    fun readFully() = testSuspend {
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

}
