package io.ktor.utils.io.tests

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.charsets.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

class BytePacketReadTest {
    val pool: VerifyingObjectPool<ChunkBuffer> = VerifyingObjectPool(ChunkBuffer.Pool)

    @AfterTest
    fun verifyPool() {
        pool.assertEmpty()
    }

    @Test
    fun testReadText() {
        val packet = buildPacket {
            writeByte(0xc6.toByte())
            writeByte(0x86.toByte())
        }

        assertEquals("\u0186", packet.readText(charset = Charsets.UTF_8))
        assertEquals(0, packet.remaining)
    }

    @Test
    fun testReadTextLimited() {
        val packet = buildPacket {
            writeByte(0xc6.toByte())
            writeByte(0x86.toByte())
            writeByte(0xc6.toByte())
            writeByte(0x86.toByte())
        }

        assertEquals("\u0186", packet.readText(charset = Charsets.UTF_8, max = 1))
        assertEquals(2, packet.remaining)
        packet.release()
    }

    @Test
    fun testReadTextChain() {
        val segment1 = pool.borrow()
        val segment2 = pool.borrow()
        segment1.next = segment2
        segment1.reserveEndGap(8)

        segment1.writeByte(0xc6.toByte())
        segment2.writeByte(0x86.toByte())

        val packet = ByteReadPacket(segment1, pool)

        assertEquals("\u0186", packet.readText())
        assertTrue { packet.isEmpty }
    }

    @Test
    fun testReadTextChainThroughReservation() {
        val segment1 = pool.borrow()
        val segment2 = pool.borrow()
        segment1.next = segment2
        segment1.reserveEndGap(8)

        while (segment1.writeRemaining > 1) {
            segment1.writeByte(0)
        }
        segment1.writeByte(0xc6.toByte())
        while (segment1.readRemaining > 1) {
            segment1.readByte()
        }
        segment2.writeByte(0x86.toByte())

        val packet = ByteReadPacket(segment1, pool)

        assertEquals("\u0186", packet.readText())
        assertTrue { packet.isEmpty }
    }

    @Test
    fun testReadTextChainWithDecoder() {
        val segment1 = pool.borrow()
        val segment2 = pool.borrow()
        segment1.next = segment2
        segment1.reserveEndGap(8)

        segment1.writeByte(0xc6.toByte())
        segment2.writeByte(0x86.toByte())

        val packet = ByteReadPacket(segment1, pool)
        assertEquals(2, packet.remaining)

        assertEquals("\u0186", packet.readText(charset = Charsets.UTF_8))
        assertTrue { packet.isEmpty }
    }


    @Test
    fun testReadBytesAll() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf(1, 2, 3, 4).contentEquals(pkt.readBytes()) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesExact1() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf(1, 2, 3, 4).contentEquals(pkt.readBytes(4)) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesExact2() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf(1, 2).contentEquals(pkt.readBytes(2)) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesExact3() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf().contentEquals(pkt.readBytes(0)) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesExactFails() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertFails {
                pkt.readBytes(9)
            }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesOf1() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf(1, 2, 3).contentEquals(pkt.readBytesOf(2, 3)) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesOf2() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf(1, 2, 3, 4).contentEquals(pkt.readBytesOf(2, 9)) }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadBytesOf3() {
        val pkt = buildPacket {
            writeInt(0x01020304)
        }

        try {
            assertTrue { byteArrayOf().contentEquals(pkt.readBytesOf(0, 0)) }
        } finally {
            pkt.release()
        }
    }


    @Test
    fun testReadBytesOfFails() {
        val pkt = buildPacket {
            writeInt(0x11223344)
        }

        try {
            assertFails {
                pkt.readBytesOf(9, 13)
            }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadShortWithEndian() {
        val pkt = buildPacket {
            writeFully(byteArrayOf(1, 2))
        }

        try {
            pkt.copy().use { copy ->
                assertEquals(0x0102, copy.readShort())
            }
            pkt.copy().use { copy ->
                assertEquals(0x0201, copy.readShortLittleEndian())
            }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadIntWithEndian() {
        val pkt = buildPacket {
            writeFully(byteArrayOf(1, 2, 3, 4))
        }

        try {
            pkt.copy().use { copy ->
                assertEquals(0x01020304, copy.readInt())
            }
            pkt.copy().use { copy ->
                assertEquals(0x04030201, copy.readIntLittleEndian())
            }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadLongWithEndian() {
        val pkt = buildPacket {
            writeFully(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        }

        try {
            pkt.copy().use { copy ->
                assertEquals(0x0102030405060708L, copy.readLong())
            }
            pkt.copy().use { copy ->
                assertEquals(0x0807060504030201, copy.readLongLittleEndian())
            }
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadFloatWithEndian() {
        val pkt = buildPacket {
            writeFully(byteArrayOf(63, -64, 0, 0))
            writeFully(byteArrayOf(0, 0, -64, 63))
        }

        try {
            assertEquals(1.5f, pkt.readFloat())
            assertEquals(1.5f, pkt.readFloatLittleEndian())
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadDoubleWithEndian() {
        val pkt = buildPacket {
            writeFully(byteArrayOf(63, -8, 0, 0, 0, 0, 0, 0))
            writeFully(byteArrayOf(0, 0, 0, 0, 0, 0, -8, 63))
        }

        try {
            assertEquals(1.5, pkt.readDouble())
            assertEquals(1.5, pkt.readDoubleLittleEndian())
        } finally {
            pkt.release()
        }
    }

    @Test
    fun testReadingFromExternallyCreatedPacket() {
        ByteReadPacket(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)).use { pkt ->
            assertEquals(0x0102030405060708L, pkt.readLong())
        }

        // simulate segment reusing
        val chunk = pool.borrow()
        chunk.writeFully(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))

        ByteReadPacket(chunk, 8L, pool).use { pkt ->
            assertEquals(0x0102030405060708L, pkt.readLong())
        }
    }

    @Test
    fun tryPeekTest() {
        buildPacket {
            writeByte(1)
            writeByte(2)
        }.use { pkt ->
            assertEquals(1, pkt.tryPeek())
            pkt.discardExact(1)
            assertEquals(2, pkt.tryPeek())
            pkt.discardExact(1)
            assertEquals(-1, pkt.tryPeek())
        }

        assertEquals(-1, ByteReadPacket.Empty.tryPeek())

        val segment1 = pool.borrow()
        val segment2 = pool.borrow()
        segment1.resetForWrite()
        segment2.resetForWrite()
        segment1.next = segment2
        segment2.writeByte(1)

        ByteReadPacket(segment1, pool).use { pkt ->
            assertEquals(1, pkt.tryPeek())
            pkt.discardExact(1)
            assertEquals(-1, pkt.tryPeek())
        }
    }

    private inline fun buildPacket(startGap: Int = 0, block: BytePacketBuilder.() -> Unit): ByteReadPacket {
        val builder = BytePacketBuilder(startGap, pool)
        try {
            block(builder)
            return builder.build()
        } catch (t: Throwable) {
            builder.release()
            throw t
        }
    }

}
