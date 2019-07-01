package io.ktor.utils.io.tests

import io.ktor.utils.io.core.*
import io.ktor.utils.io.nio.*
import java.io.*
import java.nio.channels.*
import kotlin.test.*

class ChannelsTest {
    @Test
    fun testInput() {
        val content = ByteArrayInputStream(byteArrayOf(0x11, 0x22, 0x33, 0x44))
        val input = Channels.newChannel(content).asInput()

        assertEquals(0x11223344, input.readInt())
    }

    @Test
    fun testInputBig() {
        val array = ByteArray(16384) { ((it and 0x0f) + 'a'.toInt()).toByte() }

        val content = ByteArrayInputStream(array)
        val input = Channels.newChannel(content).asInput()

        var iteration = 0
        while (!input.endOfInput) {
            input.peekEquals("erfr", iteration)
            input.discardExact(1)
            iteration++
        }

        assertEquals(array.size, iteration)
    }

    @Test
    fun testOutput() {
        val baos = ByteArrayOutputStream()
        val output = Channels.newChannel(baos).asOutput()

        output.writeInt(0x11223344)
        output.flush()

        val result = baos.toByteArray()
        assertTrue { byteArrayOf(0x11, 0x22, 0x33, 0x44).contentEquals(result) }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun Input.peekEquals(text: String, iteration: Int): Boolean {
        var equals = false

        takeWhileSize(text.length) { buffer ->
            val remaining = buffer.readRemaining
            verify(buffer)
            val sourceText = buffer.readText(max = text.length)
            equals = sourceText == text
            buffer.rewind(remaining - buffer.readRemaining)
            0
        }
        return equals
    }

    private fun verify(buffer: Buffer) {
        var failedAt = -1

        buffer.read { memory, start, endExclusive ->
            var prev = memory.loadAt(start)
            for (index in start until endExclusive) {
                val value = memory.loadAt(index)
                val delta = value - prev
                if (delta != 1 && delta != -15 && index != start) {
                    failedAt = index - start
                    break
                }
                prev = value
            }
            0
        }

        if (failedAt != -1) {
            verificationFailed(buffer, failedAt)
        }
    }

    private fun verificationFailed(buffer: Buffer, errorIndex: Int): Nothing {
        buffer.read { memory, start, endExclusive ->
            print(buildString(endExclusive - start + errorIndex + 3) {
                for (index in start until endExclusive) {
                    append(memory.loadAt(index).toInt().toChar())
                }
                append('\n')
                repeat(errorIndex) {
                    append(' ')
                }
                append("^\n")
            })
            0
        }

        fail("Verification failed, see log")
    }
}
