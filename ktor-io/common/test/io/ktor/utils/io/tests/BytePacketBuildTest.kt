package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.Test
import kotlin.test.*

open class BytePacketBuildTest {
    open val pool: VerifyingObjectPool<ChunkBuffer> = VerifyingObjectPool(ChunkBuffer.NoPool)

    @AfterTest
    fun verifyPool() {
        pool.assertEmpty()
    }

    @Test
    fun smokeSingleBufferTest() {
        val p = buildPacket {
            val ba = ByteArray(2)
            ba[0] = 0x11
            ba[1] = 0x22
            writeFully(ba)

            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.25)
            writeFloat(1.25f)
            writeLong(0x123456789abcdef0)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(2 + 1 + 2 + 4 + 8 + 4 + 8 + 8 + 3 + 5, p.remaining)
        val ba = ByteArray(2)
        p.readFully(ba)

        assertEquals(0x11, ba[0])
        assertEquals(0x22, ba[1])

        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.25, p.readDouble())
        assertEquals(1.25f, p.readFloat())

        val ll = (1..8).map { p.readByte().toInt() and 0xff }.joinToString()
        assertEquals("18, 52, 86, 120, 154, 188, 222, 240", ll)
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())

        assertTrue { p.isEmpty }
    }

    @Test
    fun smokeMultiBufferTest() {
        val p = buildPacket {
            writeFully(ByteArray(9999))
            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.25)
            writeFloat(1.25f)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(9999 + 1 + 2 + 4 + 8 + 4 + 8 + 3 + 5, p.remaining)

        p.readFully(ByteArray(9999))
        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.25, p.readDouble())
        assertEquals(1.25f, p.readFloat())
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())
        assertTrue { p.isEmpty }
    }

    @Test
    fun testSingleBufferSkipTooMuch() {
        val p = buildPacket {
            writeFully(ByteArray(9999))
        }

        assertEquals(9999, p.discard(10000))
        assertTrue { p.isEmpty }
    }

    @Test
    fun testSingleBufferSkip() {
        val p = buildPacket {
            writeFully("ABC123".toByteArray0())
        }

        assertEquals(3, p.discard(3))
        assertEquals("123", p.readUTF8Line())
        assertTrue { p.isEmpty }
    }

    @Test
    fun testSingleBufferSkipExact() {
        val p = buildPacket {
            writeFully("ABC123".toByteArray0())
        }

        p.discardExact(3)
        assertEquals("123", p.readUTF8Line())
        assertTrue { p.isEmpty }
    }

    @Test
    fun testSingleBufferSkipExactTooMuch() {
        val p = buildPacket {
            writeFully("ABC123".toByteArray0())
        }

        assertFailsWith<EOFException> {
            p.discardExact(1000)
        }
        assertTrue { p.isEmpty }
    }

    @Test
    fun testMultiBufferSkipTooMuch() {
        val p = buildPacket {
            writeFully(ByteArray(99999))
        }

        assertEquals(99999, p.discard(1000000))
        assertTrue { p.isEmpty }
    }

    @Test
    fun testMultiBufferSkip() {
        val p = buildPacket {
            writeFully(ByteArray(99999))
            writeFully("ABC123".toByteArray0())
        }

        assertEquals(99999 + 3, p.discard(99999 + 3))
        assertEquals("123", p.readUTF8Line())
        assertTrue { p.isEmpty }
    }

    @Test
    fun testNextBufferBytesStealing() {
        val p = buildPacket {
            repeat(PACKET_BUFFER_SIZE + 3) {
                writeByte(1)
            }
        }

        assertEquals(PACKET_BUFFER_SIZE + 3, p.remaining.toInt())
        p.readFully(ByteArray(PACKET_BUFFER_SIZE - 1))
        assertEquals(0x01010101, p.readInt())
        assertTrue { p.isEmpty }
    }

    @Test
    fun testNextBufferBytesStealingFailed() {
        val p = buildPacket {
            repeat(PACKET_BUFFER_SIZE + 1) {
                writeByte(1)
            }
        }

        p.readFully(ByteArray(PACKET_BUFFER_SIZE - 1))

        try {
            p.readInt()
            fail()
        } catch (expected: EOFException) {
        } finally {
            p.release()
        }
    }

    @Test
    fun testPreview() {
        val p = buildPacket {
            writeInt(777)

            val i = preview { tmp ->
                tmp.readInt()
            }

            assertEquals(777, i)
        }

        assertEquals(777, p.readInt())
    }

    @Test
    fun testReadByteEmptyPacket() {
        assertFailsWith<EOFException> {
            ByteReadPacket.Empty.readByte()
        }

        assertFailsWith<EOFException> {
            val p = buildPacket {
                writeInt(1)
            }

            try {
                p.readInt()
                p.readByte()
            } finally {
                p.release()
            }
        }
    }

    @Test
    fun testWriteShortWithEndian() {
        buildPacket {
            writeShort(0x0102)
            writeShortLittleEndian(0x0102)
        }.use { pkt ->
            assertEquals(byteArrayOf(1, 2, 2, 1).toList(), pkt.readBytes().toList())
        }
    }

    @Test
    fun testWriteIntWithEndian() {
        buildPacket {
            writeInt(0x01020304)
            writeIntLittleEndian(0x01020304)
        }.use { pkt ->
            assertEquals(byteArrayOf(1, 2, 3, 4, 4, 3, 2, 1).toList(), pkt.readBytes().toList())
        }
    }

    @Test
    fun testWriteLongWithEndian() {
        buildPacket {
            writeLong(0x0102030405060708L)
            writeLongLittleEndian(0x0102030405060708L)
        }.use { pkt ->
            assertEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 8, 7, 6, 5, 4, 3, 2, 1).toList(), pkt.readBytes().toList())
        }
    }

    @Test
    fun testWriteFloatWithEndian() {
        buildPacket {
            writeFloat(1.5f)
            writeFloatLittleEndian(1.5f)
        }.use { pkt ->
            assertEquals(byteArrayOf(63, -64, 0, 0, 0, 0, -64, 63).toList(), pkt.readBytes().toList())
        }
    }

    @Test
    fun testWriteDoubleWithEndian() {
        buildPacket {
            writeDouble(1.5)
            writeDoubleLittleEndian(1.5)
        }.use { pkt ->
            assertEquals(
                byteArrayOf(63, -8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -8, 63).toList(),
                pkt.readBytes().toList()
            )
        }
    }

    private inline fun buildPacket(block: BytePacketBuilder.() -> Unit): ByteReadPacket {
        val builder = BytePacketBuilder(0, pool)
        try {
            block(builder)
            return builder.build()
        } catch (t: Throwable) {
            builder.release()
            throw t
        }
    }

    private fun String.toByteArray0(): ByteArray {
        val result = ByteArray(length)

        for (i in 0 until length) {
            val v = this[i].toInt() and 0xff
            if (v > 0x7f) fail()
            result[i] = v.toByte()
        }

        return result
    }

    companion object {
        const val PACKET_BUFFER_SIZE: Int = 4096
    }
}
