package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.Test
import org.junit.Rule
import java.nio.*
import kotlin.test.*

class BytePacketBuildTestExtended : BytePacketBuildTest() {
    @get:Rule
    override val pool = VerifyingObjectPool(ChunkBuffer.Pool)

    @Test
    fun smokeSingleBufferTestExtended() {
        val p = buildPacket {
            writeFully(kotlin.ByteArray(2))
            writeFully(ByteBuffer.allocate(3))

            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.23)
            writeFloat(1.23f)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(2 + 3 + 1 + 2 + 4 + 8 + 4 + 8 + 3 + 5, p.remaining)

        p.readFully(ByteArray(2))
        p.readFully(ByteBuffer.allocate(3))

        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.23, p.readDouble())
        assertEquals(1.23f, p.readFloat())
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())
    }

    @Test
    fun smokeMultiBufferTestExtended() {
        val p = buildPacket {
            writeFully(ByteArray(9999))
            writeFully(ByteBuffer.allocate(8888))
            writeByte(0x12)
            writeShort(0x1234)
            writeInt(0x12345678)
            writeDouble(1.23)
            writeFloat(1.23f)
            writeLong(0x123456789abcdef0)

            writeText("OK\n")
            listOf(1, 2, 3).joinTo(this, separator = "|")
        }

        assertEquals(9999 + 8888 + 1 + 2 + 4 + 8 + 4 + 8 + 3 + 5, p.remaining)

        p.readFully(ByteArray(9999))
        p.readFully(ByteBuffer.allocate(8888))
        assertEquals(0x12, p.readByte())
        assertEquals(0x1234, p.readShort())
        assertEquals(0x12345678, p.readInt())
        assertEquals(1.23, p.readDouble())
        assertEquals(1.23f, p.readFloat())
        assertEquals(0x123456789abcdef0, p.readLong())

        assertEquals("OK", p.readUTF8Line())
        assertEquals("1|2|3", p.readUTF8Line())
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
}
