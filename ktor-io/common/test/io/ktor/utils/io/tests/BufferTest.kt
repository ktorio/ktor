package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

class BufferTest {
    private val pool = VerifyingObjectPool(ChunkBuffer.Pool)
    private val Buffer.Companion.Pool get() = pool

    @Test
    fun smokeTest() {
        assertEquals(0, Buffer.Empty.capacity)
        assertEquals(0, Buffer.Empty.readRemaining)
        assertEquals(0, Buffer.Empty.writeRemaining)
        assertEquals(0, Buffer.Empty.startGap)
        assertEquals(0, Buffer.Empty.endGap)
        assertFalse(Buffer.Empty.canRead())
        assertFalse(Buffer.Empty.canWrite())

        val buffer = Buffer.Pool.borrow()

        try {
            assertNotEquals(0, buffer.writeRemaining)
            assertEquals(buffer.capacity, buffer.writeRemaining)
            assertTrue(buffer.canWrite())
            buffer.writeInt(0x11223344)
            assertEquals(4, buffer.readRemaining)
            assertEquals(0x11223344, buffer.readInt())
            assertEquals(0, buffer.readRemaining)
        } finally {
            buffer.release(Buffer.Pool)
        }
    }

    @Test
    fun testResetForWrite() {
        val buffer = Buffer.Pool.borrow()
        try {
            val capacity = buffer.capacity

            buffer.resetForWrite(7)
            assertEquals(7, buffer.writeRemaining)
            assertEquals(0, buffer.readRemaining)

            buffer.resetForWrite()
            assertEquals(capacity, buffer.writeRemaining)
            assertEquals(0, buffer.readRemaining)
        } finally {
            buffer.release(Buffer.Pool)
        }
    }

    @Test
    fun testWriteWhenImpossible() {
        val buffer = Buffer.Pool.borrow()
        try {
            buffer.resetForRead()
            assertFails {
                buffer.writeInt(1)
            }
        } finally {
            buffer.release(Buffer.Pool)
        }
    }
}
