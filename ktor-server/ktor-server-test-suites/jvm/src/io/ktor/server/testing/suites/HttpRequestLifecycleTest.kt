/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

abstract class HttpRequestLifecycleTest<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(
    val engine: ApplicationEngineFactory<TEngine, TConfiguration>
) : EngineTestBase<TEngine, TConfiguration>(engine) {
    @Test
    fun testClientDisconnectionCancelsRequest() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestCancelled = CompletableDeferred<Unit>()

        createAndStartServer {
            install(plugin = HttpRequestLifecycle) {
                cancelCallOnClose = true
            }

            get("/") {
                requestStarted.complete(Unit)
                runCatching {
                    repeat(100) {
                        call.coroutineContext.ensureActive()
                        delay(100.milliseconds)
                    }
                }.onFailure { err ->
                    if (err is CancellationException) {
                        @OptIn(InternalAPI::class)
                        assertTrue(err.rootCause is ConnectionClosedException)
                        requestCancelled.complete(Unit)
                    } else throw err
                }
            }
        }

        val client = HttpClient()
        val requestJob = launch {
            client.get("http://127.0.0.1:$port/")
        }

        withTimeout(5.seconds) {
            requestStarted.await() // Wait for the request to start processing on the server
        }

        // Give the request some time to start processing
        delay(500.milliseconds)

        // Cancel the request and close the client to force TCP to disconnect
        requestJob.cancel()
        client.close()

        withTimeout(5.seconds) {
            requestCancelled.await() // Wait for the request to be canceled
        }
    }

    @Test
    fun testHttpRequestLifecycleSuccess() = runTest {
        val requestCompleted = CompletableDeferred<Unit>()

        createAndStartServer {
            install(plugin = HttpRequestLifecycle) {
                cancelCallOnClose = true
            }

            get("/") {
                call.respondText("OK")
                requestCompleted.complete(Unit)
            }
        }

        val client = HttpClient()
        val response = client.get("http://127.0.0.1:$port/")

        withTimeout(5.seconds) {
            requestCompleted.await()
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK", response.bodyAsText())
    }

    @Test
    fun testHttpRequestLifecycleWithStream() = runTest {
        val requestCompleted = CompletableDeferred<Unit>()

        createAndStartServer {
            install(plugin = HttpRequestLifecycle) {
                cancelCallOnClose = true
            }

            get("/") {
                call.respondOutputStream {
                    repeat(3) {
                        write("OK;".toByteArray())
                        flush()
                        delay(100.milliseconds)
                    }
                    requestCompleted.complete(Unit)
                }
            }
        }

        val client = HttpClient()
        val response = client.get("http://127.0.0.1:$port/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("OK;OK;OK;", response.bodyAsText())

        withTimeout(5.seconds) {
            requestCompleted.await()
        }
    }
}
