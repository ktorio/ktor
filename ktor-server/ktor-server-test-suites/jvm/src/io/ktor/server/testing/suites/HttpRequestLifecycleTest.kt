/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

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
import kotlinx.coroutines.channels.Channel
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.concurrent.atomics.incrementAndFetch
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
    @OptIn(ExperimentalAtomicApi::class)
    fun testClientDisconnectionCancelsRequest() = runTest {
        val requestStartedCnt = AtomicInt(0)
        val requestCancelledCnt = AtomicInt(0)

        val requestStarted = Channel<Int>(Channel.UNLIMITED)
        val requestCancelled = Channel<Int>(Channel.UNLIMITED)

        cancellableRoute {
            requestStarted.send(requestStartedCnt.incrementAndFetch())
            try {
                // very long operation
                repeat(100) {
                    call.coroutineContext.ensureActive()
                    delay(200.milliseconds)
                }
            } catch (err: CancellationException) {
                @OptIn(InternalKtorApi::class)
                assertTrue(err.rootCause is ConnectionClosedException)
                requestCancelled.send(requestCancelledCnt.incrementAndFetch())
            }
        }

        fun resetRequestOnStart(request: suspend () -> Unit) = launch {
            client = createApacheClient()
            client.use {
                val requestJob = launch {
                    runCatching { request() }
                }
                withTimeout(10.seconds) {
                    requestStarted.receive() // Wait for the request to start processing on the server
                }
                // Cancel the request and close the client to force TCP to disconnect
                requestJob.cancel()
            }
        }

        buildList {
            resetRequestOnStart {
                withHttp1("http://127.0.0.1:$port", port, {}, {})
            }.also { add(it) }
            if (enableSsl) {
                resetRequestOnStart {
                    withHttp1("https://127.0.0.1:$sslPort", sslPort, {}, {})
                }.also { add(it) }
            }
            if (enableSsl && enableHttp2) {
                resetRequestOnStart {
                    withHttp2("https://127.0.0.1:$sslPort", sslPort, {}, {})
                }.also { add(it) }
            }
        }.joinAll()

        withTimeout(10.seconds) {
            do {
                // Wait for the request to be canceled
                val cancelledCount = requestCancelled.receive()
            } while (cancelledCount < requestStartedCnt.load())
        }
    }

    @Test
    fun testHttpRequestLifecycleSuccess() = runTest {
        val requestCompleted = CompletableDeferred<Unit>()

        cancellableRoute {
            delay(100.milliseconds)
            call.respondText("OK")
            requestCompleted.complete(Unit)
        }

        client = createApacheClient()
        client.use {
            withUrl("/") {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals("OK", bodyAsText())
            }
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
                    delay(100.milliseconds)
                }
                requestCompleted.complete(Unit)
            }
        }

        client = createApacheClient()
        client.use {
            withUrl("/") {
                assertEquals(HttpStatusCode.OK, status)
                assertEquals(ContentType.Application.OctetStream, contentType())
                assertEquals("OK;OK;OK;", bodyAsText())
            }
        }

        withTimeout(10.seconds) {
            requestCompleted.await()
        }
    }
}
