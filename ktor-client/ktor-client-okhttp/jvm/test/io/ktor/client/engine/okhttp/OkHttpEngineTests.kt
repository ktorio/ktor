/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.test.*
import kotlinx.coroutines.*
import okhttp3.*
import okio.Buffer
import okio.BufferedSource
import java.util.concurrent.*
import kotlin.test.*

class OkHttpEngineTests {
    @Test
    fun testClose() {
        val okHttpClient = OkHttpClient()
        val engine = OkHttpEngine(OkHttpConfig().apply { preconfigured = okHttpClient })
        engine.close()

        assertFalse("OkHttp dispatcher is not working.") { okHttpClient.dispatcher.executorService.isShutdown }
        assertEquals(0, okHttpClient.connectionPool.connectionCount())
        okHttpClient.cache?.let { assertFalse("OkHttp client cache is closed.") { it.isClosed } }
    }

    @Test
    fun testThreadLeak() = runBlocking {
        val initialNumberOfThreads = Thread.getAllStackTraces().size

        repeat(25) {
            HttpClient(OkHttp).use { client ->
                val response = client.get("http://www.google.com").body<String>()
                assertNotNull(response)
            }
        }

        val totalNumberOfThreads = Thread.getAllStackTraces().size
        val threadsCreated = totalNumberOfThreads - initialNumberOfThreads
        assertTrue { threadsCreated < 25 }
    }

    @Test
    fun testPreconfigured() = runBlocking {
        var preconfiguredClientCalled = false
        val okHttpClient = OkHttpClient().newBuilder().addInterceptor(
            Interceptor { chain ->
                preconfiguredClientCalled = true
                chain.proceed(chain.request())
            }
        ).connectTimeout(1, TimeUnit.MILLISECONDS).build()

        HttpClient(OkHttp) {
            engine { preconfigured = okHttpClient }
        }.use { client ->
            runCatching { client.get("http://localhost:1234").body<String>() }
            assertTrue(preconfiguredClientCalled)
        }
    }

    /**
     * Closing an OkHttp response body performs blocking socket I/O, so it must not run on the thread that
     * cancels the call: on Android that thread is usually the main one, where such I/O fails with
     * `NetworkOnMainThreadException`.
     */
    @Test
    fun testResponseBodyIsClosedOnEngineDispatcher() = runTest {
        val engineThread = CompletableDeferred<Thread>()
        val closingThread = CompletableDeferred<Thread>()

        val engineExecutor = Executors.newSingleThreadExecutor { task ->
            Thread(task, "okhttp-engine-test").also { engineThread.complete(it) }
        }

        engineExecutor.asCoroutineDispatcher().use { engineDispatcher ->
            val responseBody = object : ResponseBody() {
                private val source = Buffer()

                override fun contentType(): MediaType? = null

                override fun contentLength(): Long = 0

                override fun source(): BufferedSource = source

                override fun close() {
                    super.close()
                    closingThread.complete(Thread.currentThread())
                }
            }

            val okHttpClient = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    Response.Builder()
                        .request(chain.request())
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(responseBody)
                        .build()
                }
                .build()

            val client = HttpClient(OkHttp) {
                engine {
                    preconfigured = okHttpClient
                    dispatcher = engineDispatcher
                }
            }

            client.use {
                val responseReceived = CompletableDeferred<Unit>()
                val request = launch(Dispatchers.Default) {
                    client.prepareGet("http://localhost/").execute {
                        responseReceived.complete(Unit)
                        awaitCancellation()
                    }
                }

                responseReceived.await()
                request.cancelAndJoin()

                assertSame(
                    engineThread.await(),
                    closingThread.await(),
                    "The response body must be closed on the engine dispatcher"
                )
            }
        }
    }

    @Test
    fun testRequestAfterRecreate() {
        runBlocking {
            HttpClient(OkHttp)
                .close()

            HttpClient(OkHttp).use { client ->
                val response = client.get("http://www.google.com").body<String>()
                assertNotNull(response)
            }
        }
    }
}
