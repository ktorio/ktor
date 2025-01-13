/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import io.ktor.test.dispatcher.testSuspend
import io.ktor.utils.io.*
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WriterReaderTest {

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testWriterOnCancelled() = runTest {
        val job1 = Job()
        job1.cancel()
        val writer1 = GlobalScope.writer(coroutineContext = job1) {
        }
        assertFailsWith<CancellationException> {
            writer1.channel.readByte()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    @Test
    fun testReaderOnCancelled() = testSuspend {
        val job1 = Job()
        job1.cancel()
        val reader1 = GlobalScope.reader(coroutineContext = job1) {
        }
        delay(100L)
        assertFailsWith<CancellationException> {
            reader1.channel.writeByte(42)
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
