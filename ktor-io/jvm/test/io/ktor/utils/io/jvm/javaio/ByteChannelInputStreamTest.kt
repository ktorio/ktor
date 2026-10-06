/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.jvm.javaio

import io.ktor.test.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
class ByteChannelInputStreamTest {

    @Test
    fun `read is interrupted when thread is interrupted`() {
        val channel = ByteChannel()
        val parent = Job()
        val inputStream = channel.asInputStream(parent)
        val readStarted = CountDownLatch(1)
        val result = CompletableFuture<Throwable?>()
        val readThread = thread(isDaemon = true) {
            readStarted.countDown()
            result.complete(runCatching { inputStream.read() }.exceptionOrNull())
        }

        try {
            assertTrue(readStarted.await(5, TimeUnit.SECONDS))
            assertFalse(result.isDone)
            readThread.interrupt()

            assertIs<InterruptedException>(result.get(5, TimeUnit.SECONDS))
            assertTrue(parent.isActive)
            assertFalse(channel.isClosedForRead)
        } finally {
            channel.cancel()
            parent.cancel()
            readThread.interrupt()
            readThread.join(5_000)
        }
    }

    @Test
    fun `read is cancelled with parent`() = runTest(timeout = 5.seconds) {
        val channel = ByteChannel()
        val parent = Job(coroutineContext.job)
        val inputStream = channel.asInputStream(parent)
        val readStarted = CompletableDeferred<Unit>()
        val result = async(Dispatchers.IO) {
            readStarted.complete(Unit)
            runCatching { inputStream.read() }.exceptionOrNull()
        }

        channel.use {
            readStarted.await()
            parent.cancel()

            assertIs<CancellationException>(result.await())
        }
    }
}
