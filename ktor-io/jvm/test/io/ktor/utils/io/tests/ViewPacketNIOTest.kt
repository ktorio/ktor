package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import java.nio.*
import kotlin.test.*

class ViewPacketNIOTest {
    @Test
    fun testArrayShortcut() {
        val array = byteBufferOf(1, 2, 3)

        val packet = ByteReadPacket(array)

        assertEquals(3, packet.discard())
    }

    @Test
    fun testArrayViewDiscard() {
        val array = byteBufferOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        assertEquals(3, packet.discard())

        assertTrue { recycled }
    }

    @Test
    fun testArrayViewRelease() {
        val array = byteBufferOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        packet.release()

        assertTrue { recycled }
    }

    @Test
    fun testArrayViewReading() {
        val array = byteBufferOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        assertEquals(1, packet.readByte())
        assertFalse { recycled }

        assertEquals(2, packet.readByte())
        assertFalse { recycled }

        assertEquals(3, packet.readByte())
        assertTrue { recycled }
    }

    private fun byteBufferOf(vararg bytes: Byte): ByteBuffer {
        return ByteBuffer.wrap(bytes)
    }
}
