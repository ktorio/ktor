package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import kotlin.test.*

class ViewPacketTest {
    @Test
    fun testArrayShortcut() {
        val array = byteArrayOf(1, 2, 3)

        val packet = ByteReadPacket(array)

        assertEquals(3, packet.discard())
    }

    @Test
    fun testArrayViewDiscard() {
        val array = byteArrayOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array, 1, 1) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        packet.discard()

        assertTrue { recycled }
    }

    @Test
    fun testArrayViewRelease() {
        val array = byteArrayOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array, 1, 1) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        packet.release()

        assertTrue { recycled }
    }

    @Test
    fun testArrayViewReading() {
        val array = byteArrayOf(1, 2, 3)
        var recycled = false

        val packet = ByteReadPacket(array, 1, 1) {
            assertSame(array, it)
            recycled = true
        }

        assertFalse { recycled }

        assertEquals(2, packet.readByte())

        assertTrue { recycled }
    }
}
