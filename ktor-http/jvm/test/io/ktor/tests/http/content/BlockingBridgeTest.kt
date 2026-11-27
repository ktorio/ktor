/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.content

import io.ktor.http.content.*
import io.ktor.test.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(InternalAPI::class)
@CoroutinesTimeout(testTimeoutMs = 5_000)
class BlockingBridgeTest {

    private val executors = mutableListOf<ExecutorService>()

    @AfterTest
    fun tearDown() {
        executors.forEach { it.shutdownNow() }
    }

    private fun namedDispatcher(name: String): CoroutineDispatcher {
        val executor = Executors.newSingleThreadExecutor { Thread(it, name).apply { isDaemon = true } }
        executors += executor
        return executor.asCoroutineDispatcher()
    }

    // Limit parallelism to 1 thread to make it easier to exhaust the dispatcher
    private val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1, "test-io-dispatcher")

    @Test
    fun `withBlockingOutputStream in combination with reader should not exhaust dispatcher`() = runTest {
        val readerStarted = CompletableDeferred<Unit>()

        val channel = reader(singleThreadDispatcher + CoroutineName("reader")) {
            readerStarted.complete(Unit)
            val buffer = ByteArray(1)
            while (channel.readAvailable(buffer) != -1) {
                // consume until close
            }
        }.channel

        readerStarted.await()
        launch(singleThreadDispatcher + CoroutineName("writer")) {
            channel.use {
                // Direct usage of channel.toOutputStream().use { ... } would exhaust the dispatcher
                withBlockingOutputStream(singleThreadDispatcher) { stream ->
                    stream.write(42)
                }
            }
        }
    }

    @Test
    fun `withBlockingOutputStream propagates cancellation`() = runTest(timeout = 5.seconds) {
        val channel = ByteChannel()
        val writeStarted = CompletableDeferred<Unit>()
        val writer = async(singleThreadDispatcher + CoroutineName("writer")) {
            channel.withBlockingOutputStream(singleThreadDispatcher) { stream ->
                writeStarted.complete(Unit)
                stream.write(ByteArray(2 * 1024 * 1024))
            }
        }

        writeStarted.await()
        writer.cancel()

        try {
            assertFailsWith<CancellationException> { writer.await() }
        } finally {
            channel.cancel()
        }
    }

    @Test
    fun `withBlockingOutputStream uses dispatcher from context element`() = runTest {
        val custom = namedDispatcher("element-dispatcher")
        val channel = ByteChannel()
        var threadName: String? = null

        withContext(BlockingDispatcherElement(custom)) {
            channel.withBlockingOutputStream { stream ->
                threadName = Thread.currentThread().name
                stream.write(42)
            }
        }
        channel.close()

        assertTrue(threadName?.startsWith("element-dispatcher") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `withBlockingWriter uses dispatcher from context element`() = runTest {
        val custom = namedDispatcher("element-dispatcher")
        val channel = ByteChannel()
        var threadName: String? = null

        withContext(BlockingDispatcherElement(custom)) {
            channel.withBlockingWriter(Charsets.UTF_8) { writer ->
                threadName = Thread.currentThread().name
                writer.write("Hello")
            }
        }
        channel.close()

        assertTrue(threadName?.startsWith("element-dispatcher") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `explicit dispatcher takes priority over context element`() = runTest {
        val explicit = namedDispatcher("explicit-dispatcher")
        val fromElement = namedDispatcher("element-dispatcher")
        val channel = ByteChannel()
        var threadName: String? = null

        withContext(BlockingDispatcherElement(fromElement)) {
            channel.withBlockingOutputStream(explicit) { stream ->
                threadName = Thread.currentThread().name
                stream.write(42)
            }
        }
        channel.close()

        assertTrue(threadName?.startsWith("explicit-dispatcher") == true, "Unexpected thread: $threadName")
    }

    @Test
    fun `withBlockingOutputStream falls back to default bridge without element`() = runTest {
        val channel = ByteChannel()
        var threadName: String? = null

        channel.withBlockingOutputStream { stream ->
            threadName = Thread.currentThread().name
            stream.write(42)
        }
        channel.close()

        assertFalse(threadName.isNullOrEmpty())
        assertFalse(threadName!!.startsWith("element-dispatcher"))
    }

    @Test
    fun `withBlockingWriter in combination with reader should not exhaust dispatcher`() = runTest {
        val readerStarted = CompletableDeferred<Unit>()

        val channel = reader(singleThreadDispatcher + CoroutineName("reader")) {
            readerStarted.complete(Unit)
            val buffer = ByteArray(64)
            while (channel.readAvailable(buffer) != -1) {
                // consume until close
            }
        }.channel

        readerStarted.await()
        launch(singleThreadDispatcher + CoroutineName("writer")) {
            channel.use {
                // Direct usage of channel.writer().use { ... } would exhaust the dispatcher
                withBlockingWriter(Charsets.UTF_8, singleThreadDispatcher) { writer ->
                    writer.write("Hello")
                }
            }
        }
    }
}
