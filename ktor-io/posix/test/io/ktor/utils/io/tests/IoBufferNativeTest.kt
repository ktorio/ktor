package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.pool.*
import kotlinx.cinterop.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

class IoBufferNativeTest {
    private val buffer = ChunkBuffer.Pool.borrow()

    @AfterTest
    fun destroy() {
        buffer.release(ChunkBuffer.Pool)
    }

    @Test
    fun testReadDirectOnEmpty() {
        var invoked = false
        buffer.readDirect { ptr ->
            invoked = true
            0
        }.also {
            assertEquals(0, it)
        }
        assertTrue(invoked)
    }

    @Test
    fun testReadDirectNegativeResult() {
        var invoked = false
        assertFails {
            buffer.readDirect { ptr ->
                -1
            }
        }
    }

    @Test
    fun testReadDirectTooManyBytesResult() {
        var invoked = false
        assertFails {
            buffer.readDirect { ptr ->
                1
            }
        }
    }

    @Test
    fun testReadDirect() {
        var result = 0
        buffer.writeByte(7)
        buffer.writeByte(8)
        buffer.readDirect { ptr ->
            result = ptr[0].toInt()
            1
        }.also {
            assertEquals(1, it)
        }
        assertEquals(7, result)
        assertEquals(8, buffer.readByte().toInt())
    }

    @Test
    fun testReadDirectAtEnd() {
        while (buffer.writeRemaining > 0) {
            buffer.writeByte(1)
        }

        val size = buffer.readRemaining
        buffer.readDirect { ptr ->
            buffer.readRemaining
        }.also {
            assertEquals(size, it)
        }

        assertEquals(0, buffer.readRemaining)
        buffer.readDirect { 0 }.also {
            assertEquals(0, it)
        }
    }

    @Test
    fun testWriteDirect() {
        buffer.writeDirect { ptr ->
            ptr[0] = 1.toByte()
            ptr[1] = 2.toByte()
            2
        }.also {
            assertEquals(2, it)
        }

        assertEquals(2, buffer.readRemaining)
        assertEquals(1, buffer.readByte().toInt())
        assertEquals(2, buffer.readByte().toInt())
    }

    @Test
    fun testWriteDirectOnFull() {
        val size = buffer.writeRemaining
        buffer.writeDirect { size }
        assertEquals(size, buffer.readRemaining)
        assertEquals(0, buffer.writeRemaining)
        buffer.writeDirect { 0 }
    }
}
