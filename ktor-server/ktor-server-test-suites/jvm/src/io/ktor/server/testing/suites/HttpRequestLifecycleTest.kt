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

    private suspend fun cancellableRoute(handler: RoutingHandler) {
        createAndStartServer {
            install(plugin = HttpRequestLifecycle) {
                cancelCallOnClose = true
            }
            get(handler)
        }
    }

    @Test
    fun testClientDisconnectionCancelsRequest() = runTest {
        val requestStarted = CompletableDeferred<Unit>()
        val requestCancelled = CompletableDeferred<Unit>()

        cancellableRoute {
            requestStarted.complete(Unit)
            try {
                // very long operation
                repeat(100) {
                    call.coroutineContext.ensureActive()
                    delay(200.milliseconds)
                }
            } catch (err: CancellationException) {
                @OptIn(InternalAPI::class)
                assertTrue(err.rootCause is ConnectionClosedException)
                requestCancelled.complete(Unit)
            }
        }

        HttpClient().use { client ->
            val requestJob = launch {
                client.get("http://127.0.0.1:$port/")
            }

            withTimeout(5.seconds) {
                requestStarted.await() // Wait for the request to start processing on the server
            }

            // Cancel the request and close the client to force TCP to disconnect
            requestJob.cancel()
        }

        withTimeout(10.seconds) {
            requestCancelled.await() // Wait for the request to be canceled
        }
    }

    @Test
    fun testHttpRequestLifecycleSuccess() = runTest {
        val requestCompleted = CompletableDeferred<Unit>()

        cancellableRoute {
            call.respondText("OK")
            requestCompleted.complete(Unit)
        }

        HttpClient().use { client ->
            val response = client.get("http://127.0.0.1:$port/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK", response.bodyAsText())
        }

        withTimeout(10.seconds) {
            requestCompleted.await()
        }
    }

    @Test
    fun testHttpRequestLifecycleWithStream() = runTest {
        val requestCompleted = CompletableDeferred<Unit>()

        cancellableRoute {
            call.respondOutputStream {
                repeat(3) {
                    write("OK;".toByteArray())
                    flush()
                    delay(100.milliseconds)
                }
                requestCompleted.complete(Unit)
            }
        }

        HttpClient().use { client ->
            val response = client.get("http://127.0.0.1:$port/")
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("OK;OK;OK;", response.bodyAsText())
        }

        withTimeout(10.seconds) {
            requestCompleted.await()
        }
    }
}
