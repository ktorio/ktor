/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RawSourceChannelTest {

    @Test
    fun `awaitContent returns false when channel is closed with empty buffer`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()

        // First call closes the channel internally (EOF)
        val firstResult = channel.awaitContent(1)
        assertFalse(firstResult, "awaitContent should return false on EOF")

        // Second call hits the closedToken != null path
        val secondResult = channel.awaitContent(1)
        assertFalse(secondResult, "awaitContent should return false when channel is already closed with empty buffer")
    }

    @Test
    fun `awaitContent returns true when channel is closed with data in buffer`() = runTest {
        val channel = ByteArray(10) { it.toByte() }.inputStream().toByteReadChannel()

        // Request more than available to trigger EOF and close the channel
        val firstResult = channel.awaitContent(20)
        assertFalse(firstResult, "awaitContent should return false when not enough data")

        // Channel is now closed, but 10 bytes remain in the buffer
        val secondResult = channel.awaitContent(1)
        assertTrue(secondResult, "awaitContent should return true when closed channel still has data in buffer")
    }

    @Test
    fun `awaitContent throws when channel is cancelled`() = runTest {
        val channel = ByteArray(0).inputStream().toByteReadChannel()
        channel.cancel(IOException("test cancellation"))
        assertFailsWith<CancellationException> {
            channel.awaitContent(1)
        }
    }

    @Test
    fun `awaitContent completes when caller coroutine is cancelled on blocking stream`() {
        val closeLatch = CountDownLatch(1)

        val blockingInput = object : java.io.InputStream() {
            override fun read(): Int {
                closeLatch.await()
                return -1
            }

            override fun close() {
                closeLatch.countDown()
            }
        }

        val channel = blockingInput.toByteReadChannel()

        runBlocking {
            val job = launch(Dispatchers.IO) {
                channel.readFully(ByteArray(1024))
            }

            Thread.sleep(500)

            job.cancel()

            withTimeout(5000) {
                job.join()
            }
        }
    }
}
