package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.math.*
import kotlin.test.*

class PrimitiveCodecTest {
    val pool = VerifyingObjectPool(ChunkBuffer.Pool)
    val builder = BytePacketBuilder(0, pool)

    @AfterTest
    fun tearDown() {
        try {
            pool.assertEmpty()
        } finally {
            pool.dispose()
        }
    }

    @Test
    fun testSingleByte() {
        builder.writeByte(7)
        assertEquals(1, builder.size)
        val packet = builder.build()
        assertEquals(1, packet.remaining)
        assertEquals(7, packet.readByte())
        assertEquals(0, packet.remaining)
        assertTrue { packet.isEmpty }
    }

    @Test
    fun testUByte() {
        builder.writeUByte(0xfeu)
        assertEquals(1, builder.size)
        val packet = builder.build()
        assertEquals(0xfeu, packet.readUByte())
    }

    @Test
    fun testWriteShortLE() {
        builder.writeShortLittleEndian(0x1100)
        assertEquals(2, builder.size)
        val p = builder.build()

        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }
        assertEquals(0, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteShortLE2() {
        builder.writeShort(0x1100, ByteOrder.LITTLE_ENDIAN)
        assertEquals(2, builder.size)
        val p = builder.build()

        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }
        assertEquals(0, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteShortBE() {
        builder.writeShort(0x1100)
        assertEquals(2, builder.size)
        val p = builder.build()

        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }
        assertEquals(0x11, p.readByte())
        assertEquals(0x00, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteShortBE2() {
        builder.writeShort(0x1100, ByteOrder.BIG_ENDIAN)
        assertEquals(2, builder.size)
        val p = builder.build()

        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }
        assertEquals(0x11, p.readByte())
        assertEquals(0x00, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadShortLE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        assertEquals(2, builder.size)
        val p = builder.build()
        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x2211, p.readShortLittleEndian())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadShortLE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        assertEquals(2, builder.size)
        val p = builder.build()
        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x2211, p.readShort(ByteOrder.LITTLE_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadShortBE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        assertEquals(2, builder.size)
        val p = builder.build()
        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x1122, p.readShort())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadShortBE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        assertEquals(2, builder.size)
        val p = builder.build()
        assertEquals(2, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x1122, p.readShort(ByteOrder.BIG_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testUShort() {
        builder.writeUShort(0xfefcu)
        val packet = builder.build()
        assertEquals(0xfefcu, packet.readUShort())
    }

    @Test
    fun testWriteIntLE() {
        builder.writeIntLittleEndian(0x11223344)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x44, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteIntLE2() {
        builder.writeInt(0x11223344, ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x44, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteIntBE() {
        builder.writeInt(0x11223344)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteIntBE2() {
        builder.writeInt(0x11223344, ByteOrder.BIG_ENDIAN)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadIntLE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x44332211, p.readIntLittleEndian())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadIntLE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x44332211, p.readInt(ByteOrder.LITTLE_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadIntBE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11223344, p.readInt())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadIntBE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11223344, p.readInt(ByteOrder.BIG_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testUInt() {
        builder.writeUInt(0xfefcfbfau)
        val packet = builder.build()
        assertEquals(0xfefcfbfau, packet.readUInt())
    }

    @Test
    fun testWriteLongLE() {
        builder.writeLongLittleEndian(0x112233440a0b0c0dL)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x0d, p.readByte())
        assertEquals(0x0c, p.readByte())
        assertEquals(0x0b, p.readByte())
        assertEquals(0x0a, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteLongLE2() {
        builder.writeLong(0x112233440a0b0c0dL, ByteOrder.LITTLE_ENDIAN)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x0d, p.readByte())
        assertEquals(0x0c, p.readByte())
        assertEquals(0x0b, p.readByte())
        assertEquals(0x0a, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x11, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteLongBE() {
        builder.writeLong(0x112233440a0b0c0dL)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0x0a, p.readByte())
        assertEquals(0x0b, p.readByte())
        assertEquals(0x0c, p.readByte())
        assertEquals(0x0d, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteLongBE2() {
        builder.writeLong(0x112233440a0b0c0dL, ByteOrder.BIG_ENDIAN)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x11, p.readByte())
        assertEquals(0x22, p.readByte())
        assertEquals(0x33, p.readByte())
        assertEquals(0x44, p.readByte())
        assertEquals(0x0a, p.readByte())
        assertEquals(0x0b, p.readByte())
        assertEquals(0x0c, p.readByte())
        assertEquals(0x0d, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadLongLE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        builder.writeByte(0x0a)
        builder.writeByte(0x0b)
        builder.writeByte(0x0c)
        builder.writeByte(0x0d)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x0d0c0b0a44332211L, p.readLongLittleEndian())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadLongLE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        builder.writeByte(0x0a)
        builder.writeByte(0x0b)
        builder.writeByte(0x0c)
        builder.writeByte(0x0d)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x0d0c0b0a44332211L, p.readLong(ByteOrder.LITTLE_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadLongBE() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        builder.writeByte(0x0a)
        builder.writeByte(0x0b)
        builder.writeByte(0x0c)
        builder.writeByte(0x0d)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x112233440a0b0c0dL, p.readLong())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadLongBE2() {
        builder.writeByte(0x11)
        builder.writeByte(0x22)
        builder.writeByte(0x33)
        builder.writeByte(0x44)
        builder.writeByte(0x0a)
        builder.writeByte(0x0b)
        builder.writeByte(0x0c)
        builder.writeByte(0x0d)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x112233440a0b0c0dL, p.readLong(ByteOrder.BIG_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testULong() {
        builder.writeULong(0xfffefdfcfbfaf9f8u)
        val packet = builder.build()
        val hex: String
        packet.copy().read {
            hex = it.readHex()
            it.readRemaining
        }
        assertEquals("fffefdfcfbfaf9f8", hex)
        assertEquals(0xfffefdfcfbfaf9f8u, packet.readULong())
    }

    @Test
    fun testWriteFloatLE() {
        builder.writeFloatLittleEndian(0.05f)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(-51, p.readByte())
        assertEquals(-52, p.readByte())
        assertEquals(76, p.readByte())
        assertEquals(61, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteFloatLE2() {
        builder.writeFloat(0.05f, ByteOrder.LITTLE_ENDIAN)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(-51, p.readByte())
        assertEquals(-52, p.readByte())
        assertEquals(76, p.readByte())
        assertEquals(61, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteFloatBE() {
        builder.writeFloat(0.05f)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(61, p.readByte())
        assertEquals(76, p.readByte())
        assertEquals(-52, p.readByte())
        assertEquals(-51, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteFloatBE2() {
        builder.writeFloat(0.05f, ByteOrder.BIG_ENDIAN)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(61, p.readByte())
        assertEquals(76, p.readByte())
        assertEquals(-52, p.readByte())
        assertEquals(-51, p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadFloatLE() {
        builder.writeByte(-51)
        builder.writeByte(-52)
        builder.writeByte(76)
        builder.writeByte(61)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(5, (p.readFloatLittleEndian() * 100.0f).roundToInt())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadFloatLE2() {
        builder.writeByte(-51)
        builder.writeByte(-52)
        builder.writeByte(76)
        builder.writeByte(61)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(5, (p.readFloat(ByteOrder.LITTLE_ENDIAN) * 100.0f).roundToInt())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadFloatBE() {
        builder.writeByte(61)
        builder.writeByte(76)
        builder.writeByte(-52)
        builder.writeByte(-51)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(5, (p.readFloat() * 100.0f).roundToInt())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadFloatBE2() {
        builder.writeByte(61)
        builder.writeByte(76)
        builder.writeByte(-52)
        builder.writeByte(-51)
        assertEquals(4, builder.size)
        val p = builder.build()
        assertEquals(4, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(5, (p.readFloat(ByteOrder.BIG_ENDIAN) * 100.0f).roundToInt())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteDoubleLE() {
        builder.writeDoubleLittleEndian(0.05)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x9a.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0xa9.toByte(), p.readByte())
        assertEquals(0x3f.toByte(), p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteDoubleLE2() {
        builder.writeDouble(0.05, ByteOrder.LITTLE_ENDIAN)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x9a.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0xa9.toByte(), p.readByte())
        assertEquals(0x3f.toByte(), p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteDoubleBE() {
        builder.writeDouble(0.05)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x3f.toByte(), p.readByte())
        assertEquals(0xa9.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x9a.toByte(), p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testWriteDoubleBE2() {
        builder.writeDouble(0.05, ByteOrder.BIG_ENDIAN)
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0x3f.toByte(), p.readByte())
        assertEquals(0xa9.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x99.toByte(), p.readByte())
        assertEquals(0x9a.toByte(), p.readByte())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadDoubleLE() {
        builder.writeByte(0x9a.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0xa9.toByte())
        builder.writeByte(0x3f.toByte())
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0.05, p.readDoubleLittleEndian())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadDoubleLE2() {
        builder.writeByte(0x9a.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0xa9.toByte())
        builder.writeByte(0x3f.toByte())
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0.05, p.readDouble(ByteOrder.LITTLE_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadDoubleBE() {
        builder.writeByte(0x3f.toByte())
        builder.writeByte(0xa9.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x9a.toByte())
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0.05, p.readDouble())
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    @Test
    fun testReadDoubleBE2() {
        builder.writeByte(0x3f.toByte())
        builder.writeByte(0xa9.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x99.toByte())
        builder.writeByte(0x9a.toByte())
        assertEquals(8, builder.size)
        val p = builder.build()
        assertEquals(8, p.remaining)
        assertTrue { p.isNotEmpty }

        assertEquals(0.05, p.readDouble(ByteOrder.BIG_ENDIAN))
        assertEquals(0, p.remaining)
        assertTrue { p.isEmpty }
    }

    private fun Buffer.readHex() = buildString(readRemaining * 2) {
        repeat(readRemaining) {
            val i = readByte().toInt() and 0xff
            val l = i shr 4
            val r = i and 0x0f

            appendDigit(l)
            appendDigit(r)
        }
    }

    private fun StringBuilder.appendDigit(d: Int) {
        kotlin.require(d < 16) { "digit $d should be in [0..15]" }
        kotlin.require(d >= 0) { "digit $d should be in [0..15]" }

        if (d < 10) append('0' + d)
        else append('a' + (d - 10))
    }
}
