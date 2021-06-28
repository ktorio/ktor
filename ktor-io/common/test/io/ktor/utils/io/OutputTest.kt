package io.ktor.utils.io

import io.ktor.utils.io.bits.Memory
import io.ktor.utils.io.core.*
import io.ktor.utils.io.core.internal.*
import kotlin.test.*

class OutputTest {
    @Test
    fun smokeTest() {
        val builder = BytePacketBuilder()

        val output = object : Output() {
            override fun closeDestination() {
            }

            override fun flush(source: Memory, offset: Int, length: Int) {
                builder.writeFully(source, offset, length)
            }
        }

        output.use {
            it.append("test")
        }

        val pkt = builder.build().readText()
        assertEquals("test", pkt)
    }

    @Test
    fun testCopy() {
        val result = BytePacketBuilder()

        val output = object : Output() {
            override fun closeDestination() {
            }

            override fun flush(source: Memory, offset: Int, length: Int) {
                result.writeFully(source, offset, length)
            }
        }

        val fromHead = ChunkBuffer.Pool.borrow()
        var current = fromHead
        repeat(3) {
            current.append("test $it. ")
            val next = ChunkBuffer.Pool.borrow()
            current.next = next
            current = next
        }

        current.append("end.")

        val from = ByteReadPacket(fromHead, ChunkBuffer.Pool)

        from.copyTo(output)
        output.flush()

        assertEquals("test 0. test 1. test 2. end.", result.build().readText())
    }
}
