package org.jetbrains.ktor.tests.nio

import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.util.*
import org.junit.*
import java.nio.*
import kotlin.test.*

class AsyncPumpTest {
    @Test
    fun testSimplyCopy() {
        doCopy("Hello, async!")
    }

    @Test
    fun testCopyWithStep() {
        val text = "Hello, async!"

        for (step in 1..text.length) {
            doCopy(text, step)
        }
    }

    @Test
    fun testBigText() {
        val text = buildString {
            for (i in 1..100) {
                appendln("${" ".repeat(i % 5)}Oh my async, $i")
            }
        }

        doCopy(text)
        doCopy(text, 16)
    }

    private fun doCopy(text: String, step: Int = Int.MAX_VALUE) = runSync {
        val source = asyncOf(text, step)
        val out = ByteBufferWriteChannel()
        source.copyTo(out)

        assertEquals(text, out.toString(Charsets.ISO_8859_1))
    }

    private fun asyncOf(text: String, step: Int = Int.MAX_VALUE) = asyncOf(ByteBuffer.wrap(text.toByteArray(Charsets.ISO_8859_1)), step)
    private fun asyncOf(bb: ByteBuffer, step: Int = Int.MAX_VALUE) = ByteBufferReadChannel(bb, step)
}