package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.nio.*
import io.ktor.utils.io.streams.*
import org.junit.Test
import org.junit.Ignore
import java.io.*
import java.nio.*
import java.nio.ByteOrder
import java.nio.channels.*
import kotlin.test.*

class PacketInteropTest {
    private val baos = ByteArrayOutputStream()
    private val out = Channels.newChannel(baos)!!

    private val bais by lazy { ByteArrayInputStream(baos.toByteArray()) }
    private val input by lazy { Channels.newChannel(bais)!! }

    @Test
    fun testStream() {
        baos.writePacket {
            writeInt(777)
            writeLong(0x1234567812345678L)
            writeText("OK")
        }

        val result = ByteBuffer.wrap(baos.toByteArray())!!
        result.order(ByteOrder.BIG_ENDIAN)
        assertEquals(777, result.getInt())
        assertEquals(0x1234567812345678L, result.getLong())
        assertEquals(0x4f4b, result.getShort())

        val p = bais.readPacketExact(14)
        assertEquals(777, p.readInt())
        assertEquals(0x1234567812345678L, p.readLong())
        assertEquals("OK", p.readUTF8Line())
    }

    @Test
    fun testStreamLong() {
        baos.writePacket {
            writeInt(777)
            writeLong(0x1234567812345678L)

            repeat(4000) {
                append("OK")
            }
        }

        val result = ByteBuffer.wrap(baos.toByteArray())!!
        result.order(ByteOrder.BIG_ENDIAN)
        assertEquals(777, result.getInt())
        assertEquals(0x1234567812345678L, result.getLong())
        repeat(4000) {
            assertEquals(0x4f4b, result.getShort())
        }

        val p = bais.readPacketExact(baos.size().toLong())
        assertEquals(777, p.readInt())
        assertEquals(0x1234567812345678L, p.readLong())
        repeat(4000) {
            assertEquals(0x4f4b, p.readShort())
        }
    }

    @Test
    fun testChannel() {
        out.writePacket {
            writeInt(777)
            writeLong(0x1234567812345678L)
            writeText("OK")
        }
        out.close()

        val result = ByteBuffer.wrap(baos.toByteArray())!!
        result.order(ByteOrder.BIG_ENDIAN)
        assertEquals(777, result.getInt())
        assertEquals(0x1234567812345678L, result.getLong())
        assertEquals(0x4f4b, result.getShort())

        val p = input.readPacketExact(14)
        assertEquals(777, p.readInt())
        assertEquals(0x1234567812345678L, p.readLong())
        assertEquals("OK", p.readUTF8Line())
    }

    @Test
    fun testChannelLong() {
        out.writePacket {
            writeInt(777)
            writeLong(0x1234567812345678L)
            repeat(4000) {
                append("OK")
            }
        }
        out.close()

        val result = ByteBuffer.wrap(baos.toByteArray())!!
        result.order(ByteOrder.BIG_ENDIAN)
        assertEquals(777, result.getInt())
        assertEquals(0x1234567812345678L, result.getLong())
        repeat(4000) {
            assertEquals(0x4f4b, result.getShort())
        }

        val p = input.readPacketExact(baos.size().toLong())
        assertEquals(777, p.readInt())
        assertEquals(0x1234567812345678L, p.readLong())
        repeat(4000) {
            assertEquals(0x4f4b, p.readShort())
        }
    }

    @Test
    fun testStreamReadPacketAtLeast() {
        baos.writePacket {
            writeInt(0x12345678)
        }

        assertEquals(4, bais.readPacketAtLeast(1).remaining)
        assertEquals(0, bais.available())
    }

    @Test
    fun testStreamReadPacketAtMost() {
        baos.writePacket {
            writeInt(0x12345678)
        }

        assertEquals(1, bais.readPacketAtMost(1).remaining)
        assertEquals(3, bais.available())
    }

    @Test
    fun testChannelReadPacketAtLeast() {
        baos.writePacket {
            writeInt(0x12345678)
        }

        assertEquals(4, input.readPacketAtLeast(1).remaining)
        assertEquals(0, bais.available())
    }

    @Test
    fun testChannelReadPacketAtMost() {
        baos.writePacket {
            writeInt(0x12345678)
        }

        assertEquals(1, input.readPacketAtMost(1).remaining)
        assertEquals(3, bais.available())
    }
}
