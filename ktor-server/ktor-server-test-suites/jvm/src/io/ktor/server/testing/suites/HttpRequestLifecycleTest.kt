/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.suites

import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.http.*
import io.ktor.server.plugins.calllogging.*
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

    @OptIn(ExperimentalAtomicApi::class)
    private fun testDisconnection(
        startServerWithRoute: suspend (suspend RoutingContext.() -> Unit) -> Unit
    ) = runTest(retries = 3) {
        val requestStartedCnt = AtomicInt(0)
        val requestCancelledCnt = AtomicInt(0)

        val requestStarted = Channel<Int>(Channel.UNLIMITED)
        val requestCancelled = Channel<Int>(Channel.UNLIMITED)

        startServerWithRoute {
            requestStarted.send(requestStartedCnt.incrementAndFetch())
            try {
                // very long operation
                repeat(100) {
                    call.coroutineContext.ensureActive()
                    delay(200.milliseconds)
                }
            } catch (err: CancellationException) {
                @OptIn(InternalAPI::class)
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
    fun testClientDisconnectionCancelsRequest() {
        testDisconnection { configureRoute ->
            cancellableRoute(configureRoute)
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

    @Test
    fun testHttpRequestLifecycleWithCallLogging() = runTest {
        val server = createServer {
            install(HttpRequestLifecycle) {
                cancelCallOnClose = true
            }
            install(CallLogging) {
                mdc("something") { "something else" }
            }
            routing {
                get("/hello") {
                    call.respondText("world")
                }
            }
        }
        startServer(server)

        client = createApacheClient()
        client.use {
            repeat(20) {
                withUrl("/hello") {
                    assertEquals(HttpStatusCode.OK, status)
                    assertEquals("world", bodyAsText())
                }
            }
        }
    }

    @Test
    fun testHttpRequestLifecycleCancelWithCallLogging() {
        testDisconnection { configureRoute ->
            val server = createServer {
                install(HttpRequestLifecycle) {
                    cancelCallOnClose = true
                }
                install(CallLogging) {
                    mdc("something") { "something else" }
                }
                routing {
                    get { configureRoute() }
                }
            }
            startServer(server)
        }
    }

    @Test
    @OptIn(ExperimentalAtomicApi::class)
    open fun testPipelinedRequestsCancelledOnDisconnect() = runTest {
        val pipelinedCount = 10
        val allStarted = Channel<Unit>(pipelinedCount)
        val cancelledCount = AtomicInt(0)
        val allCancelled = CompletableDeferred<Unit>()

        val server = createServer {
            install(HttpRequestLifecycle) {
                cancelCallOnClose = true
            }
            routing {
                get("/slow") {
                    allStarted.send(Unit)
                    try {
                        repeat(100) {
                            delay(200.milliseconds)
                        }
                        call.respondText("Done")
                    } catch (e: CancellationException) {
                        val count = cancelledCount.incrementAndFetch()
                        if (count == pipelinedCount) {
                            allCancelled.complete(Unit)
                        }
                        throw e
                    }
                }
            }
        }
        startServer(server)

        SelectorManager().use { selector ->
            aSocket(selector).tcp().connect("127.0.0.1", port) {
                lingerSeconds = 0
            }.use { socket ->
                val output = socket.openWriteChannel()
                repeat(pipelinedCount) {
                    output.writeStringUtf8("GET /slow HTTP/1.1\r\n")
                    output.writeStringUtf8("Host: localhost:$port\r\n")
                    output.writeStringUtf8("Connection: keep-alive\r\n")
                    output.writeStringUtf8("\r\n")
                }
                output.flush()

                withTimeout(10.seconds) {
                    repeat(pipelinedCount) {
                        allStarted.receive()
                    }
                }

                socket.close()
                socket.awaitClosed()
            }
        }

        withTimeout(10.seconds) {
            allCancelled.await()
        }
        assertEquals(pipelinedCount, cancelledCount.load())
    }

    @Test
    fun testConnectionCloseRequestCompletesSuspendingHandler() = runTest {
        val handlerStarted = CompletableDeferred<Unit>()
        val resumeHandler = CompletableDeferred<Unit>()
        val handlerOutcome = CompletableDeferred<String>()

        val server = createServer {
            install(HttpRequestLifecycle) {
                cancelCallOnClose = true
            }
            routing {
                post("/slow") {
                    handlerStarted.complete(Unit)
                    try {
                        resumeHandler.await()
                        call.respondText("pong")
                        handlerOutcome.complete("completed")
                    } catch (cause: CancellationException) {
                        handlerOutcome.complete("cancelled")
                        throw cause
                    }
                }
            }
        }
        startServer(server)

        val response = SelectorManager().use { selector ->
            aSocket(selector).tcp().connect("127.0.0.1", port) {
                socketTimeout = 30.seconds.inWholeMilliseconds
            }.use { socket ->
                val output = socket.openWriteChannel()
                val input = socket.openReadChannel()
                val body = """{"key":"value"}"""
                val httpMessage = "POST /slow HTTP/1.1\r\n" +
                    "Host: localhost:$port\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: ${body.length}\r\n" +
                    "Connection: close\r\n" +
                    "\r\n"
                output.writeStringUtf8(httpMessage)
                output.flush()

                handlerStarted.await()
                output.writeStringUtf8(body)
                output.flush()

                resumeHandler.complete(Unit)
                input.readRemaining().readText()
            }
        }
        val outcome = withTimeoutOrNull(5.seconds) { handlerOutcome.await() } ?: "still suspended"
        assertTrue(
            response.contains("\r\n\r\n"),
            "Malformed HTTP response: missing header terminator; " +
                "server closed after ${response.length} bytes (handler outcome: $outcome): \"$response\"",
        )
        assertTrue(
            response.startsWith("HTTP/1.1 200"),
            "Expected a 200 response (handler outcome: $outcome), got: \"$response\"",
        )
        assertTrue(
            response.endsWith("pong"),
            "Expected the full body (handler outcome: $outcome), got: \"$response\"",
        )
        assertEquals("completed", outcome, "Full response received, so the handler must have run to completion")
    }
}
