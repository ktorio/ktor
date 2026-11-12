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
import kotlin.test.assertNull

/** Long enough to rule out scheduling noise, short enough to fail fast on a regression. */
private const val TIMEOUT = 5000L

/** Awaiting data that must not arrive; kept short to avoid stalling the suite. */
private const val NOT_ARRIVING_TIMEOUT = 500L

class WriterReaderTest {

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

    @Test
    fun testWriterAutoFlushPublishesBeforeBlockCompletes() = runTestWithRealTime {
        val writer = writer(autoFlush = true) {
            channel.writeByte(42)
            awaitCancellation()
        }

        assertEquals(42, withTimeout(TIMEOUT) { writer.channel.readByte() })
        writer.cancel()
    }

    @Test
    fun testReaderAutoFlushPublishesBeforeBlockCompletes() = runTestWithRealTime {
        val received = CompletableDeferred<Byte>()
        val reader = reader(autoFlush = true) {
            received.complete(channel.readByte())
            awaitCancellation()
        }

        reader.channel.writeByte(42)

        assertEquals(42, withTimeout(TIMEOUT) { received.await() })
        reader.cancel()
    }

    @Test
    fun testWriterWithoutAutoFlushWaitsForFlush() = runTestWithRealTime {
        val writer = writer(autoFlush = false) {
            channel.writeByte(42)
            awaitCancellation()
        }

        assertNull(withTimeoutOrNull(NOT_ARRIVING_TIMEOUT) { writer.channel.readByte() })
        writer.cancel()
    }

    @Test
    fun testReaderWithoutAutoFlushWaitsForFlush() = runTestWithRealTime {
        val received = CompletableDeferred<Byte>()
        val reader = reader(autoFlush = false) {
            received.complete(channel.readByte())
            awaitCancellation()
        }

        reader.channel.writeByte(42)

        assertNull(withTimeoutOrNull(NOT_ARRIVING_TIMEOUT) { received.await() })
        reader.cancel()
    }
}
