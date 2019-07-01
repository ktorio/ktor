package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import io.ktor.utils.io.streams.*
import org.junit.Test
import org.junit.Rule
import java.util.*
import kotlin.test.*

class BytePacketReaderWriterTest {
    @get:Rule
    internal val pool = VerifyingObjectPool(ChunkBuffer.Pool)

    @Test
    fun testReaderEmpty() {
        val packet = buildPacket {
        }

        assertNotNull(packet)
        assertEquals(-1, packet.readerUTF8().read())
    }

    @Test
    fun testReaderFew() {
        val packet = buildPacket {
            append("ABC")
        }

        assertEquals("ABC", packet.readerUTF8().readText())
    }

    @Test
    fun testReaderMultiple() {
        val s = buildString {
            repeat(100000) {
                this.append("e")
            }
        }

        val packet = buildPacket {
            append(s)
        }

        assertEquals(s, packet.readerUTF8().readText())
    }

    @Test
    fun testReaderFewUtf() {
        val s = "\u0447"
        val packet = buildPacket {
            append(s)
        }

        assertEquals(s, packet.readerUTF8().readText())
    }

    @Test
    fun testReaderFewUtf3bytes() {
        val s = "\u0BF5"
        val packet = buildPacket {
            append(s)
        }

        assertEquals(s, packet.readerUTF8().readText())
    }

    @Test
    fun testReaderMultipleUtf() {
        val s = buildString {
            repeat(100000) {
                append("\u0447")
            }
        }

        val packet = buildPacket {
            append(s)
        }

        assertEquals(s, packet.readerUTF8().readText())
    }

    @Test
    fun testReaderMultipleUtf3bytes() {
        val s = buildString {
            repeat(100000) {
                append("\u0BF5")
            }
        }

        val packet = buildPacket {
            append(s)
        }

        assertEquals(s, packet.readerUTF8().readText())
    }

    @Test
    fun testWriterSingleBufferSingleWrite() {
        val s = buildString {
            append("ABC")
        }

        val packet = buildPacket {
            writerUTF8().write(s)
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterSingleBufferSingleWriteUtf() {
        val s = buildString {
            append("A\u0447C")
        }

        val packet = buildPacket {
            writerUTF8().write(s)
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterSingleBufferMultipleWrite() {
        val s = buildString {
            append("ABC")
        }

        val packet = buildPacket {
            writerUTF8().apply {
                write(s.substring(0, 1))
                write(s.substring(1))
            }
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterSingleBufferMultipleWriteUtf() {
        val s = buildString {
            append("\u0447BC")
            append("A\u0447C")
            append("AB\u0447")
            append("\u0447")
        }

        val packet = buildPacket {
            writerUTF8().let { w ->
                w.write("\u0447BC")
                w.write("A\u0447C")
                w.write("AB\u0447")
                w.write("\u0447")
            }
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterMultiBufferSingleWrite() {
        val s = buildString {
            repeat(100000) {
                append("x")
            }
        }

        val packet = buildPacket {
            writerUTF8().write(s)
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterMultiBufferSingleWriteUtf() {
        val s = buildString {
            repeat(100000) {
                append("A\u0447")
            }
        }

        val packet = buildPacket {
            writerUTF8().write(s)
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }

    @Test
    fun testWriterMultiBufferSingleWriteUtf3bytes() {
        val s = buildString {
            repeat(100000) {
                append("\u0BF5")
            }
        }

        val packet = buildPacket {
            writerUTF8().write(s)
        }

        assertEquals(s, packet.inputStream().readBytes().toString(Charsets.UTF_8))
    }



    @Test
    fun testSingleBufferReadAll() {
        val bb = ByteArray(100)
        Random().nextBytes(bb)

        val p = buildPacket {
            writeFully(bb)
        }

        assertTrue { bb.contentEquals(p.readBytes()) }
    }

    @Test
    fun testMultiBufferReadAll() {
        val bb = ByteArray(100000)
        Random().nextBytes(bb)

        val p = buildPacket {
            writeFully(bb)
        }

        assertTrue { bb.contentEquals(p.readBytes()) }
    }

    @Test
    fun testCopySingleBufferPacket() {
        val bb = ByteArray(100)
        Random().nextBytes(bb)

        val p = buildPacket {
            writeFully(bb)
        }

        val copy = p.copy()
        assertEquals(p.remaining, p.remaining)
        assertTrue { p.readBytes().contentEquals(copy.readBytes()) }
    }

    @Test
    fun testCopyMultipleBufferPacket() {
        val bb = ByteArray(1000000)
        Random().nextBytes(bb)

        val p = buildPacket {
            writeFully(bb)
        }

        val copy = p.copy()
        assertEquals(p.remaining, p.remaining)
        val bytes = p.readBytes()
        val copied = copy.readBytes()

        assertTrue { bytes.contentEquals(copied) }
    }

    @Test
    fun testWritePacketSingle() {
        val inner = buildPacket {
            append("ABC")
            assertEquals(3, size)
        }

        val outer = buildPacket {
            append("123")
            kotlin.test.assertEquals(3, size)
            writePacket(inner)
            kotlin.test.assertEquals(6, size)
            append(".")
        }

        assertEquals("123ABC.", outer.readText())
        assertEquals(0, inner.remaining)
    }

    @Test
    fun testWritePacketMultiple() {
        val inner = buildPacket {
            append("o".repeat(100000))
        }

        val outer = buildPacket {
            append("123")
            assertEquals(3, size)
            writePacket(inner)
            assertEquals(100003, size)
            append(".")
        }

        assertEquals("123" + "o".repeat(100000) + ".", outer.readText())
        assertEquals(0, inner.remaining)
    }

    @Test
    fun writePacketWithHintExact() {
        val inner = buildPacket(4) {
            append(".")
        }

        val outer = buildPacket {
            append("1234")
            assertEquals(4, size)
            writePacket(inner)
            assertEquals(5, size)
        }

        assertEquals("1234.", outer.readText())
        assertEquals(0, inner.remaining)
    }

    @Test
    fun writePacketWithHintBigger() {
        val inner = buildPacket(10) {
            append(".")
        }

        val outer = buildPacket {
            append("1234")
            kotlin.test.assertEquals(4, size)
            writePacket(inner)
            kotlin.test.assertEquals(5, size)
        }

        assertEquals("1234.", outer.readText())
        assertEquals(0, inner.remaining)
    }

    @Test
    fun writePacketWithHintFailed() {
        val inner = buildPacket(3) {
            append(".")
        }

        val outer = buildPacket {
            append("1234")
            kotlin.test.assertEquals(4, size)
            writePacket(inner)
            kotlin.test.assertEquals(5, size)
        }

        assertEquals("1234.", outer.readText())
        assertEquals(0, inner.remaining)
    }

    @Test
    fun testWritePacketSingleUnconsumed() {
        val inner = buildPacket {
            append("ABC")
        }

        val outer = buildPacket {
            append("123")
            kotlin.test.assertEquals(3, size)
            writePacket(inner.copy())
            kotlin.test.assertEquals(6, size)
            append(".")
        }

        assertEquals("123ABC.", outer.readText())
        assertEquals(3, inner.remaining)
        inner.release()
    }

    @Test
    fun testWritePacketMultipleUnconsumed() {
        val inner = buildPacket {
            append("o".repeat(100000))
        }

        val outer = buildPacket {
            append("123")
            kotlin.test.assertEquals(3, size)
            writePacket(inner.copy())
            kotlin.test.assertEquals(100003, size)
            append(".")
        }

        assertEquals("123" + "o".repeat(100000) + ".", outer.readText())
        assertEquals(100000, inner.remaining)
        inner.release()
    }

    @Test
    fun testWriteDirect() {
        val packet = buildPacket {
            writeDirect(8) { bb ->
                bb.putLong(0x1234567812345678L)
            }
        }

        assertEquals(0x1234567812345678L, packet.readLong())
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

    private inline fun buildString(block: StringBuilder.() -> Unit) = StringBuilder().apply(block).toString()

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            BytePacketReaderWriterTest().testWriteDirect()
        }
    }
}
