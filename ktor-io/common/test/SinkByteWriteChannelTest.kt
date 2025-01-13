/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.utils.io.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.RawSink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail

class SinkByteWriteChannelTest {

    @Test
    fun `write byte`() = runTest {
        val buffer = Buffer()
        val sink = object : RawSink by buffer {}
        val channel = buffer.asByteWriteChannel()
        channel.writeByte(42)
        channel.flushAndClose()

        assertEquals(42, buffer.readByte())
    }

    @Test
    fun `raw sink is not closed on channel close`() = runTest {
        var flushed = false
        var read = false
        val sink = object : RawSink {
            override fun write(source: Buffer, byteCount: Long) {
                read = true
                assertEquals(1, byteCount)
                assertEquals(42, source.readByte())
            }

            override fun flush() {
                flushed = true
            }

            override fun close() {
                fail()
            }
        }

        val channel = sink.asByteWriteChannel()
        channel.writeByte(42)
        channel.flushAndClose()

        assertTrue(read, "Read was not called")
        assertTrue(flushed, "Flush was not called")
    }

    @Test
    fun `write after cancel should throw exception`() = runTest {
        val sink = createSink()
        val channel = sink.asByteWriteChannel()
        channel.close(IOException("Test"))

        assertFailsWith<IOException>("Test") {
            channel.writeByte(42)
        }
    }

    @Test
    fun `write after close should fail`() = runTest {
        val sink = createSink()
        val channel = sink.asByteWriteChannel()
        channel.flushAndClose()

        assertFailsWith<IOException> {
            channel.writeByte(42)
        }
    }

    private fun createSink(): RawSink = object : RawSink by Buffer() {}
}
