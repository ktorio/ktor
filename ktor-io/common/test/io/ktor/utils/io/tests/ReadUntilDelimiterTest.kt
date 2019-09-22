package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

class ReadUntilDelimiterTest {
    private val pool: VerifyingObjectPool<ChunkBuffer> = VerifyingObjectPool(ChunkBuffer.NoPool)
    private val small = ByteArray(3)
    private val empty = ByteArray(0)
    private val builder = BytePacketBuilder(0, pool)

    @AfterTest
    fun verifyPool() {
        pool.assertEmpty()
    }

    @Test
    fun testEmpty() {
        val src = ByteReadPacket.Empty
        assertEquals(0, src.readUntilDelimiter(0, small))
        assertEquals(0, src.readUntilDelimiter(0, empty))
        assertEquals(0, src.readUntilDelimiters(0, 1, small))
    }

    @Test
    fun discardSmoke() {
        testDiscard(0x10, src = "00 01 02 10 03", remaining = "10 03")
        testDiscard(0x10, src = "00 01 02 10", remaining = "10")
        testDiscard(0x10, src = "00 01 02 03", remaining = "")
        testDiscard(0x10, src = "", remaining = "")
    }

    @Test
    fun discardSmokeTwoDelimiters() {
        testDiscard(0x10,0x20, src = "00 01 02 10 03", remaining = "10 03")
        testDiscard(0x20,0x10, src = "00 01 02 10 03", remaining = "10 03")
        testDiscard(0x10,0x20, src = "00 01 02 20 03", remaining = "20 03")
        testDiscard(0x20,0x10, src = "00 01 02 20 03", remaining = "20 03")

        testDiscard(0x10,0x20, src = "10 20", remaining = "10 20")
        testDiscard(0x10,0x20, src = "20 10", remaining = "20 10")
    }

    @Test
    fun smokeTest() {
        test(0x10, src = "00", copied = "00", remaining = "")
        test(0x10, src = "00 10", copied = "00", remaining = "10")
        test(0x00, src = "00 10", copied = "", remaining = "00 10")

        test(0x00, src = "00 10", copied = "", remaining = "00 10", buffer = empty)
        test(0x10, src = "00 10", copied = "", remaining = "00 10", buffer = empty)

        test(0x10, src = "00 01 02 03 04", copied = "00 01 02", remaining = "03 04", buffer = small)
    }

    @Test
    fun smokeTestToOutput() {
        testToOutput(0x10, src = "00", copied = "00", remaining = "")
        testToOutput(0x10, src = "00 10", copied = "00", remaining = "10")
        testToOutput(0x00, src = "00 10", copied = "", remaining = "00 10")
    }

    @Test
    fun smokeTestTwoDelimiters() {
        test(0x10, 0x20, src = "00", copied = "00", remaining = "")

        test(0x10, 0x20, src = "00 10 20", copied = "00", remaining = "10 20")
        test(0x20, 0x10, src = "00 10 20", copied = "00", remaining = "10 20")
        test(0x10, 0x20, src = "00 20 10", copied = "00", remaining = "20 10")
        test(0x20, 0x10, src = "00 20 10", copied = "00", remaining = "20 10")

        test(0x10, 0x20, src = "01 02 03 04 10", copied = "01 02 03", remaining = "04 10")
    }

    @Test
    fun smokeTestTwoDelimitersToOutput() {
        testToOutput(0x10, 0x20, src = "00", copied = "00", remaining = "")

        testToOutput(0x10, 0x20, src = "00 10 20", copied = "00", remaining = "10 20")
        testToOutput(0x20, 0x10, src = "00 10 20", copied = "00", remaining = "10 20")
        testToOutput(0x10, 0x20, src = "00 20 10", copied = "00", remaining = "20 10")
        testToOutput(0x20, 0x10, src = "00 20 10", copied = "00", remaining = "20 10")
    }

    @Test
    fun testContinuous() {
        "11 77 22 77 33 77".unhex().use { packet ->
            var i = 0
            while (packet.isNotEmpty) {
                i += packet.readUntilDelimiter(0x77, small, i, small.size - i)
                packet.discardExact(1)
            }

            assertEquals("11 22 33", small.hexdump())
        }
    }

    @Test
    fun testContinuousToOutput() {
        "11 77 22 77 33 77".unhex().use { packet ->
            while (packet.isNotEmpty) {
                packet.readUntilDelimiter(0x77, builder)
                packet.discardExact(1)
            }

            assertEquals("11 22 33", builder.build().readBytes().hexdump())
        }
    }

    @Test
    fun multiPageTest() {
        val size = 65536
        repeat(size - 1) {
            builder.writeByte((it and 0x7f).toByte())
        }
        builder.writeByte(0x80.toByte())

        val packet = builder.build()
        try {
            val dst = ByteArray(size)
            val result = packet.readUntilDelimiter(0x80.toByte(), dst)
            assertEquals(size - 1, result)
            assertEquals(1, packet.remaining)
            packet.discard(1)
        } finally {
            packet.release()
        }
    }

    @Test
    fun multiPageTestToBuilder() {
        val size = 65536
        repeat(size - 1) {
            builder.writeByte((it and 0x7f).toByte())
        }
        builder.writeByte(0x80.toByte())

        val packet = builder.build()
        try {
            val result = packet.readUntilDelimiter(0x80.toByte(), builder)
            assertEquals(size - 1, result.toInt())
            assertEquals(result, builder.size.toLong())
            assertEquals(1, packet.remaining)
            packet.discard(1)
        } finally {
            packet.release()
            builder.release()
        }
    }

    private fun test(delimiter: Byte, src: String, copied: String, remaining: String, buffer: ByteArray = small) {
        builder.reset()
        val packet = src.unhex()
        try {
            val rc = packet.readUntilDelimiter(delimiter, buffer)
            assertEquals(copied, buffer.copyOf(rc).hexdump())
            assertEquals(remaining, packet.readBytes().hexdump())
        } finally {
            packet.release()
        }
    }

    private fun testToOutput(delimiter: Byte, src: String, copied: String, remaining: String) {
        builder.reset()
        val packet = src.unhex()
        val size = packet.remaining

        try {
            val rc = packet.readUntilDelimiter(delimiter, builder)
            assertTrue { rc <= size }
            val copiedPacket = builder.build()
            try {
                assertEquals(copied, copiedPacket.readBytes(rc.toInt()).hexdump())
                assertTrue("result packet has more bytes than returned: ${copiedPacket.remaining + rc} > $rc") {
                    copiedPacket.isEmpty }
                assertEquals(remaining, packet.readBytes().hexdump())
            } finally {
                copiedPacket.release()
            }
        } finally {
            packet.release()
        }
    }

    private fun testDiscard(delimiter: Byte, src: String, remaining: String) {
        builder.reset()
        val packet = src.unhex()
        val size = packet.remaining
        try {
            val rc = packet.discardUntilDelimiter(delimiter)
            assertEquals(size - rc, packet.remaining)
            assertEquals(remaining, packet.readBytes().hexdump())
        } finally {
            packet.release()
        }
    }

    private fun testDiscard(delimiter1: Byte, delimiter2: Byte, src: String, remaining: String) {
        builder.reset()
        val packet = src.unhex()
        val size = packet.remaining
        try {
            val rc = packet.discardUntilDelimiters(delimiter1, delimiter2)
            assertEquals(size - rc, packet.remaining)
            assertEquals(remaining, packet.readBytes().hexdump())
        } finally {
            packet.release()
        }
    }

    private fun test(delimiter1: Byte, delimiter2: Byte, src: String, copied: String, remaining: String, buffer: ByteArray = small) {
        builder.reset()
        val packet = src.unhex()
        try {
            val rc = packet.readUntilDelimiters(delimiter1, delimiter2, buffer)
            assertEquals(copied, buffer.copyOf(rc).hexdump())
            assertEquals(remaining, packet.readBytes().hexdump())
        } finally {
            packet.release()
        }
    }

    private fun testToOutput(delimiter1: Byte, delimiter2: Byte, src: String, copied: String, remaining: String) {
        builder.reset()
        val packet = src.unhex()
        val size = packet.remaining
        try {
            val rc = packet.readUntilDelimiters(delimiter1, delimiter2, builder)
            assertTrue { rc <= size }
            val copiedPacket = builder.build()
            try {
                assertEquals(copied, copiedPacket.readBytes(rc.toInt()).hexdump())
                assertTrue("result packet has more bytes than returned: ${copiedPacket.remaining + rc} > $rc") {
                    copiedPacket.isEmpty }
                assertEquals(remaining, packet.readBytes().hexdump())
            } finally {
                copiedPacket.release()
            }
        } finally {
            packet.release()
        }
    }

    private fun test(delimiter: Byte, expected: Int, remaining: Int, content: ByteArray) {
        val packet = packetOf(*content)
        try {
            assertEquals(expected, packet.readUntilDelimiter(delimiter, small))
            assertEquals(remaining.toLong(), packet.remaining)
        } finally {
            packet.release()
        }
    }

    private fun packetOf(vararg bytes: Byte): ByteReadPacket {
        builder.writeFully(bytes)
        return builder.build()
    }

    private fun String.unhex(): ByteReadPacket {
        val builder = builder
        builder.reset()

        try {
            var idx = 0
            while (idx < length) {
                val ch = this[idx]

                if (ch == ' ') {
                    idx ++
                } else {
                    if (idx + 1 >= length) throw IllegalArgumentException("Not enough hex digits found")
                    val first = ch.hexDigit()
                    val second = this[idx + 1].hexDigit()
                    idx += 2

                    builder.writeByte((first shl 4 or second).toByte())
                }
            }

            return builder.build()
        } finally {
            builder.reset()
        }
    }

    private fun isHexDigit(ch: Char) = ch in '0'..'9' || ch in 'a'..'f' || ch in 'A'..'F'

    private fun Char.hexDigit() = when (this) {
        in '0' .. '9' -> this - '0'
        in 'a' .. 'f' -> this - 'a' + 10
        in 'A' .. 'F' -> this - 'A' + 10
        else -> error("Illegal hex digit $this")
    }

    private fun ByteArray.hexdump() = joinToString(separator = " ") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
}
