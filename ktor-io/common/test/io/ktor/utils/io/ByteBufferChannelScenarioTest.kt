package io.ktor.utils.io

import io.ktor.utils.io.bits.*
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

open class ByteBufferChannelScenarioTest : ByteChannelTestBase(true) {

    @Test
    fun testReadBeforeAvailable() = runTest {
        expect(1)

        launch {
            expect(3)

            val bb = ChunkBuffer.NoPool.borrow()

            // should suspend
            val rc = ch.readAvailable(bb)

            expect(5)
            assertEquals(4, rc)

            expect(6)
        }

        expect(2)
        yield()

        expect(4)

        // should resume
        ch.writeInt(0xff)

        yield()

        finish(7)
    }

    @Test
    fun testReadBeforeAvailable2() = runTest {
        expect(1)

        launch {
            expect(3)

            val bb = ChunkBuffer.NoPool.borrow()
            bb.resetForWrite(4)
            ch.readFully(bb) // should suspend

            expect(5)

            assertEquals(4, bb.readRemaining)

            expect(6)
        }

        expect(2)
        yield()

        expect(4)
        ch.writeInt(0xff) // should resume

        yield()

        finish(7)
    }

    @Test
    fun testReadAfterAvailable() = runTest {
        expect(1)

        ch.writeInt(0xff) // should resume

        launch {
            expect(3)

            val bb = ChunkBuffer.NoPool.borrow()
            bb.resetForWrite(10)
            val rc = ch.readAvailable(bb) // should NOT suspend

            expect(4)
            assertEquals(4, rc)

            expect(5)
        }

        expect(2)
        yield()

        finish(6)
    }

    @Test
    fun testReadAfterAvailable2() = runTest {
        expect(1)

        ch.writeInt(0xff) // should resume

        launch {
            expect(3)

            val bb = ChunkBuffer.NoPool.borrow()
            bb.resetForWrite(4)
            ch.readFully(bb) // should NOT suspend

            expect(4)
            assertEquals(4, bb.readRemaining)

            expect(5)
        }

        expect(2)
        yield()

        finish(6)
    }

    @Test
    fun testReadToEmpty() = runTest {
        expect(1)

        val rc = ch.readAvailable(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(0) })

        expect(2)

        assertEquals(0, rc)

        finish(3)
    }

    @Test
    fun testReadToEmptyFromFailedChannel() = runTest {
        expect(1)

        ch.close(ExpectedException())

        try {
            ch.readAvailable(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(0) })
            fail("Should throw exception")
        } catch (expected: ExpectedException) {
        }

        finish(2)
    }

    @Test
    fun testReadToEmptyFromClosedChannel() = runTest {
        expect(1)

        ch.close()

        val rc = ch.readAvailable(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(0) })

        expect(2)

        assertEquals(-1, rc)

        finish(3)
    }

    @Test
    fun testReadFullyToEmptyFromClosedChannel() = runTest {
        expect(1)

        ch.close()

        ch.readFully(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(0) })

        finish(2)
    }

    @Test
    fun testReadFullyFromClosedChannel() = runTest() {
        expect(1)

        ch.close()
        try {
            ch.readFully(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(1) })
            fail("Should throw exception")
        } catch (expected: Throwable) {
        }

        finish(2)
    }

    @Test
    fun testReadFullyToEmptyFromFailedChannel() = runTest {
        expect(1)

        ch.close(ExpectedException())

        try {
            ch.readFully(ChunkBuffer.NoPool.borrow().also { it.resetForWrite(0) })
            fail("Should throw exception")
        } catch (expected: ExpectedException) {
        }

        finish(2)
    }

    @Test
    fun testWritePacket() = runTest {
        launch {
            expect(1)

            ch.writePacket {
                writeLong(0x1234567812345678L)
            }

            expect(2)
        }

        yield()
        expect(3)

        assertEquals(0x1234567812345678L, ch.readLong())
        assertEquals(0, ch.availableForRead)

        finish(4)
    }

    @Test
    fun testWriteBlockSuspend() = runTest {
        launch {
            expect(1)

            ch.writeFully(ByteArray(4088))

            expect(2)

            ch.writePacket(8) {
                writeLong(0x1234567812345678L)
            }

            expect(4)
        }

        yield()
        expect(3)

        ch.readFully(ByteArray(9))
        yield()
        expect(5)

        ch.readFully(ByteArray(4088 - 9))

        expect(6)

        assertEquals(0x1234567812345678L, ch.readLong())
        assertEquals(0, ch.availableForRead)

        finish(7)
    }

    @Test
    fun testNewWriteBlock() = runTest {
        launch {
            assertEquals(0x11223344, ch.readInt())
        }

        ch.write(4) { freeSpace, startOffset, endExclusive ->
            if (endExclusive - startOffset < 4) {
                fail("Not enough free space for writing 4 bytes: ${endExclusive - startOffset}")
            }

            freeSpace.storeIntAt(startOffset, 0x11223344)

            4
        }
        ch.close()
    }

    @Test
    fun testReadBlock() = runTest {
        ch.writeLong(0x1234567812345678L)
        ch.flush()

        ch.readSession {
            assertEquals(0x1234567812345678L, request()!!.readLong())
        }

        finish(1)
    }

    @Test
    fun testReadBlockSuspend() = runTest {
        ch.writeByte(0x12)

        launch {
            expect(1)
            ch.readSuspendableSession {
                await(8)
                assertEquals(0x1234567812345678L, request(8)!!.readLong())
            }

            expect(3)
        }

        yield()
        expect(2)

        ch.writeLong(0x3456781234567800L)
        yield()

        expect(4)
        ch.readByte()
        assertEquals(0, ch.availableForRead)

        finish(5)
    }

    @Test
    fun testReadBlockSuspend2() = runTest {
        launch {
            expect(1)
            ch.readSuspendableSession {
                await(8)
                assertEquals(0x1234567812345678L, request(8)!!.readLong())
            }

            expect(3)
        }

        yield()
        expect(2)

        ch.writeLong(0x1234567812345678L)
        yield()

        expect(4)
        assertEquals(0, ch.availableForRead)

        finish(5)
    }

    @Test
    fun testReadMemoryBlock() = runTest {
        launch {
            ch.writeInt(0x11223344)
            ch.close()
        }

        ch.read(4) { source, start, endExclusive ->
            if (endExclusive - start < 4) {
                fail("It should be 4 bytes available, got ${endExclusive - start}")
            }

            assertEquals(0x11223344, source.loadIntAt(start))
            4
        }

        assertEquals(0, ch.availableForRead)
    }

    @Test
    fun testWriteByteSuspend() = runTest {
        launch {
            expect(1)
            ch.writeByte(1)
            ch.writeFully(ByteArray(ch.availableForWrite))
            expect(2)
            ch.writeByte(1)
            expect(5)
            ch.close()
        }

        yield()
        expect(3)
        yield()
        expect(4)
        yield()

        ch.readByte()
        yield()

        ch.readRemaining()
        finish(6)
    }

    @Test
    fun testWriteShortSuspend() = runTest {
        launch {
            expect(1)
            ch.writeByte(1)
            ch.writeFully(ByteArray(ch.availableForWrite))
            expect(2)
            ch.writeShort(1)
            expect(5)
            ch.close()
        }

        yield()
        expect(3)
        yield()
        expect(4)
        yield()

        ch.readShort()
        yield()

        ch.readRemaining()
        finish(6)
    }

    @Test
    fun testWriteIntSuspend() = runTest {
        launch {
            expect(1)
            ch.writeByte(1)
            ch.writeFully(ByteArray(ch.availableForWrite))
            expect(2)
            ch.writeInt(1)
            expect(5)
            ch.close()
        }

        yield()
        expect(3)
        yield()
        expect(4)
        yield()

        ch.readInt()
        yield()

        ch.readRemaining()
        finish(6)
    }

    @Test
    fun testWriteIntThenRead() = runTest {
        val size = 4096 - 8 - 3

        expect(1)
        val buffer = ChunkBuffer.NoPool.borrow()
        buffer.resetForWrite(size)
        repeat(size) {
            buffer.writeByte(1)
        }

        ch.writeFully(buffer)
        expect(2)

        launch {
            expect(4)
            debug(ch.readPacket(size))
            expect(5)
        }

        // coroutine is pending
        expect(3)
        ch.writeInt(0x11223344)
        yield()
        expect(6)

        assertEquals(0x11223344, ch.readInt())

        finish(7)
    }

    @Test
    fun testWriteByteByByte() = runTest {
        ch.writeByte(1)
        ch.flush()
        ch.writeByte(2)
        ch.flush()

        assertEquals(2, ch.availableForRead)
        ch.discardExact(2)
    }

    @Test
    fun testWriteByteByByteLong() = runTest {
        launch {
            repeat(16384) {
                ch.writeByte(it and 0x0f)
                ch.flush()
            }
            ch.close()
        }

        yield()
        ch.discardExact(16384)
    }

    private fun debug(p: ByteReadPacket) {
        p.release()
    }

    @Test
    fun testWriteLongSuspend() = runTest {
        launch {
            expect(1)
            ch.writeByte(1)
            ch.writeFully(ByteArray(ch.availableForWrite))
            expect(2)
            ch.writeLong(1)
            expect(5)
            ch.close()
        }

        yield()
        expect(3)
        yield()
        expect(4)
        yield()

        ch.readLong()
        yield()

        ch.readRemaining()
        finish(6)
    }

    @Test
    fun testDiscardExisting() = runTest {
        launch {
            expect(1)
            ch.writeInt(1)
            ch.writeInt(2)
            expect(2)
        }

        yield()
        expect(3)

        assertEquals(4, ch.discard(4))
        assertEquals(2, ch.readInt())

        finish(4)
    }

    @Test
    fun testDiscardPartiallyExisting() = runTest {
        ch.writeInt(1)

        launch {
            expect(1)
            assertEquals(8, ch.discard(8))
            expect(3)
        }

        yield()
        expect(2)

        ch.writeInt(2)
        yield()

        expect(4)
        assertEquals(0, ch.availableForRead)
        finish(5)
    }

    @Test
    fun testDiscardPartiallyExisting2() = runTest {
        launch {
            expect(1)
            assertEquals(8, ch.discard(8))
            expect(4)
        }

        yield()

        expect(2)
        ch.writeInt(1)
        yield()
        expect(3)
        assertEquals(0, ch.availableForRead)

        ch.writeInt(2)
        yield()
        expect(5)
        assertEquals(0, ch.availableForRead)
        finish(6)
    }

    @Test
    fun testDiscardClose() = runTest {
        launch {
            expect(1)
            assertEquals(8, ch.discard())
            expect(4)
        }

        yield()

        expect(2)
        ch.writeInt(1)
        yield()
        ch.writeInt(2)
        yield()

        expect(3)
        ch.close()
        yield()

        finish(5)
    }

    @Test
    fun testUnicode() = runTest {
        ch.writeStringUtf8("test - \u0422")
        ch.close()

        assertEquals("test - \u0422", ch.readUTF8Line())
    }

    class ExpectedException : Exception()
}
