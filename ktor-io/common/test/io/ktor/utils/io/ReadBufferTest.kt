package io.ktor.utils.io

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.native.concurrent.*
import kotlin.test.*

@SharedImmutable
private val SIZES = intArrayOf(0, 1, 2, 3, 10, 100, 4087, 4088, 4089, 4095, 4096, 4097, 8191, 8192, 8193, 65537)

class ReadBufferTest {
    private val pool = ChunkBuffer.Pool

    private val buffer = pool.borrow().apply {
        resetForWrite()
        reserveStartGap(8)
        reserveEndGap(8)

        repeat(writeRemaining) { index ->
            append(charForIndex(index))
        }
    }
    private val initialBufferSize = buffer.readRemaining

    private val packet = buildPacket {
        repeat(SIZES.maxOrNull()!! * 2) { index ->
            append(charForIndex(index))
        }
    }

    private val initialPacketSize = packet.remaining

    @AfterTest
    fun cleanup() {
        buffer.release(pool)
        packet.release()
    }

    @Test
    fun readAvailableBFromBuffer() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = ByteArray(size + 2)
                dst[0] = 0x7f
                dst[dst.lastIndex] = 0x7f

                assertEquals(initialBufferSize - offset, buffer.readRemaining)
                val rc = buffer.readAvailable(dst, 1, size)
                assertEquals(size, rc)

                val expected =
                    byteArrayOf(0x7f) + (offset until offset + size).map {
                        charForIndex(it).toByte()
                    }.toByteArray() + byteArrayOf(0x7f)

                assertEquals(expected.hexdump(), dst.hexdump())
            }

            offset += size
        }
    }

    @Test
    fun readFullyBFromBuffer() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = ByteArray(size + 2)
                dst[0] = 0x7f
                dst[dst.lastIndex] = 0x7f

                assertEquals(initialBufferSize - offset, buffer.readRemaining)
                buffer.readFully(dst, 1, size)

                val expected =
                    byteArrayOf(0x7f) + (offset until offset + size).map {
                        charForIndex(it).toByte()
                    }.toByteArray() + byteArrayOf(0x7f)

                assertEquals(expected.hexdump(), dst.hexdump())
            }

            offset += size
        }
    }

    @Test
    fun readAvailableIoBufferFromBuffer() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = pool.borrow()
                try {
                    dst.reserveStartGap(1)
                    dst.reserveEndGap(2)

                    assertEquals(initialBufferSize - offset, buffer.readRemaining)
                    val rc = buffer.readAvailable(dst, size)
                    assertEquals(size, rc)

                    val expected = (offset until offset + size).map { charForIndex(it).toByte() }.toByteArray()

                    assertEquals(expected.hexdump(), dst.readBytes().hexdump())
                } finally {
                    dst.release(pool)
                }
            }

            offset += size
        }
    }

    @Test
    fun readFullyIoBufferFromBuffer() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = pool.borrow()
                try {
                    dst.reserveStartGap(1)
                    dst.reserveEndGap(2)

                    assertEquals(initialBufferSize - offset, buffer.readRemaining)
                    buffer.readFully(dst, size)

                    val expected = (offset until offset + size).map { charForIndex(it).toByte() }.toByteArray()

                    assertEquals(expected.hexdump(), dst.readBytes().hexdump())
                } finally {
                    dst.release(pool)
                }
            }

            offset += size
        }
    }

    @Test
    fun readAvailableBFromPacket() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = ByteArray(size + 2)
                dst[0] = 0x7f
                dst[dst.lastIndex] = 0x7f

                assertEquals(initialPacketSize - offset, packet.remaining)
                val rc = packet.readAvailable(dst, 1, size)
                assertEquals(size, rc)

                val expected =
                    byteArrayOf(0x7f) + (offset until offset + size).map {
                        charForIndex(it).toByte()
                    }.toByteArray() + byteArrayOf(0x7f)

                assertEquals(expected.hexdump(), dst.hexdump())
            }

            offset += size
        }
    }

    @Test
    fun readFullyBFromPacket() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = ByteArray(size + 2)
                dst[0] = 0x7f
                dst[dst.lastIndex] = 0x7f

                assertEquals(initialPacketSize - offset, packet.remaining)
                packet.readFully(dst, 1, size)

                val expected =
                    byteArrayOf(0x7f) + (offset until offset + size).map {
                        charForIndex(it).toByte()
                    }.toByteArray() + byteArrayOf(0x7f)

                assertEquals(expected.hexdump(), dst.hexdump())
            }

            offset += size
        }
    }

    @Test
    fun readAvailableIoBufferFromPacket() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = pool.borrow()
                try {
                    dst.reserveStartGap(1)
                    dst.reserveEndGap(2)

                    assertEquals(initialPacketSize - offset, packet.remaining)
                    val rc = packet.readAvailable(dst, size)
                    assertEquals(size, rc)

                    val expected = (offset until offset + size).map { charForIndex(it).toByte() }.toByteArray()

                    assertEquals(expected.hexdump(), dst.readBytes().hexdump())
                } finally {
                    dst.release(pool)
                }
            }

            offset += size
        }
    }

    @Test
    fun readFullyIoBufferFromPacket() {
        var offset = 0
        for (size in SIZES) {
            if (size <= buffer.readRemaining) {
                val dst = pool.borrow()
                try {
                    dst.reserveStartGap(1)
                    dst.reserveEndGap(2)

                    assertEquals(initialPacketSize - offset, packet.remaining)
                    packet.readFully(dst, size)

                    val expected = (offset until offset + size).map { charForIndex(it).toByte() }.toByteArray()

                    assertEquals(expected.hexdump(), dst.readBytes().hexdump())
                } finally {
                    dst.release(pool)
                }
            }

            offset += size
        }
    }
}

private fun charForIndex(index: Int) = "0123456789abcdef"[index and 0x0f]
private fun ByteArray.hexdump() = joinToString(separator = " ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
