/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.request.*
import kotlinx.coroutines.*
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.Source
import okio.Timeout
import okio.buffer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class OkHttpResponseBodyCloseTest {

    /**
     * Closing an OkHttp response body performs blocking socket I/O, so it must not run on the thread that
     * cancels the call: on Android that thread is usually the main one, where such I/O fails with
     * `NetworkOnMainThreadException`.
     */
    @Test
    fun `response body is not closed on the thread that cancels the call`() = runBlocking {
        val closed = CountDownLatch(1)
        val closingThread = AtomicReference<Thread?>(null)

        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(TrackingResponseBody(closed, closingThread))
                    .build()
            }
            .build()

        HttpClient(OkHttp) { engine { preconfigured = okHttpClient } }.use { client ->
            val requestJob = Job()
            val responseReceived = CompletableDeferred<Unit>()
            launch(requestJob + Dispatchers.Default) {
                client.prepareGet("http://localhost/").execute {
                    responseReceived.complete(Unit)
                    awaitCancellation()
                }
            }

            withTimeout(10_000) { responseReceived.await() }

            val cancellingThread = Thread.currentThread()
            requestJob.cancel()

            assertTrue(closed.await(10, TimeUnit.SECONDS), "The response body has not been closed")
            assertNotSame(
                cancellingThread,
                closingThread.get(),
                "The response body has been closed on the thread that cancelled the call"
            )
        }
    }

    /**
     * An empty response body that records the thread closing it.
     */
    private class TrackingResponseBody(
        private val closed: CountDownLatch,
        private val closingThread: AtomicReference<Thread?>
    ) : ResponseBody() {
        private val source: BufferedSource = EmptySource().buffer()

        override fun contentType(): MediaType = "text/plain".toMediaType()

        override fun contentLength(): Long = -1

        override fun source(): BufferedSource = source

        override fun close() {
            closingThread.compareAndSet(null, Thread.currentThread())
            closed.countDown()
            super.close()
        }
    }

    private class EmptySource : Source {
        override fun read(sink: Buffer, byteCount: Long): Long = -1

        override fun timeout(): Timeout = Timeout.NONE

        override fun close() = Unit
    }
}
