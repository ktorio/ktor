/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class WriterReaderTest {

    @OptIn(InternalAPI::class, DelicateCoroutinesApi::class)
    @Test
    fun `atomic reader cleans up when cancelled before dispatch`() = runTest {
        val parent = Job().apply { cancel() }
        var cleanedUp = false
        val reader = reader(parent, ByteChannel(), start = CoroutineStart.ATOMIC) {
            try {
                coroutineContext.ensureActive()
                error("Cancelled reader must not perform work")
            } finally {
                cleanedUp = true
            }
        }

        reader.join()
        assertTrue(reader.isCancelled)
        assertTrue(cleanedUp)
        assertFailsWith<CancellationException> { reader.channel.writeByte(42) }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testWriterOnCancelled() = runTest {
        val job = Job()
        job.cancel()
        val writer = GlobalScope.writer(coroutineContext = job) {
        }
        assertFailsWith<CancellationException> {
            writer.channel.readByte()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReaderOnCancelled() = runTestWithRealTime {
        val job = Job()
        job.cancel()
        val reader = GlobalScope.reader(coroutineContext = job) {
        }
        delay(100L)
        assertFailsWith<CancellationException> {
            reader.channel.writeByte(42)
        }
    }

    @Test
    fun testReaderWaitsNestedLaunch() = runTest {
        val incoming = ByteChannel()
        val out = reader {
            launch {
                channel.copyAndClose(incoming)
            }
        }.channel

        delay(1000)
        out.writeByte(42)
        out.flushAndClose()

        assertEquals(42, incoming.readByte())
    }

    @Test
    fun testWriterWaitsNestedLaunch() = runTest {
        val out = ByteChannel()

        val incoming = writer {
            launch {
                out.copyAndClose(channel)
            }
        }.channel

        delay(1000)
        out.writeByte(42)
        out.flushAndClose()

        assertEquals(42, incoming.readByte())
    }
}
