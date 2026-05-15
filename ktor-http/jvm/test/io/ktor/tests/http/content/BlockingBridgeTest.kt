/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.http.content

import io.ktor.http.content.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.debug.junit5.CoroutinesTimeout
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

@CoroutinesTimeout(testTimeoutMs = 5_000)
class BlockingBridgeTest {

    // Limit parallelism to 1 thread to make it easier to exhaust the dispatcher
    private val singleThreadDispatcher = Dispatchers.IO.limitedParallelism(1, "test-io-dispatcher")

    @OptIn(InternalAPI::class)
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
