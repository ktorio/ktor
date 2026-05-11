/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.jetty.jakarta

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.cio.*
import io.ktor.server.jetty.jakarta.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import io.ktor.utils.io.streams.*
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertInstanceOf
import java.util.concurrent.TimeoutException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class JettyIdleTimeoutTest : EngineTestBase<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {

    init {
        enableSsl = false
    }

    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        super.configure(configuration)
        configuration.idleTimeout = 100.milliseconds
    }

    /**
     * For this test we want to test how an unhandled CancellationException is handled,
     * so we override afterTest to avoid the test framework interpreting it as a failed test condition.
     */
    override fun afterTest() {
        try {
            super.afterTest()
        } catch (e: CancellationException) {
            if (this.testName == ::idleTimeoutCancelsCoroutineContext.name && e.unwrapRootCause() is TimeoutException) {
                return
            }
            throw e
        }
    }

    @Test
    fun idleTimeoutRequestBodyReader(): Unit = runTest {
        val requestMessage = "Hello, world!".toByteArray()

        createAndStartServer {
            post("/echo") {
                try {
                    val receiveChannel = call.receiveChannel()
                    val requestBody = receiveChannel.readRemaining().readText()
                    call.respond(requestBody)
                } catch (e: Exception) {
                    assertTrue {
                        e is TimeoutCancellationException ||
                            e is java.util.concurrent.TimeoutException
                    }
                    call.respondText(
                        "Timed out while receiving request body",
                        ContentType.Text.Plain,
                        HttpStatusCode.InternalServerError
                    )
                }
            }
        }

        // Request body sent immediately - no timeout
        withUrl("/echo", {
            method = HttpMethod.Post
            setBody(requestMessage)
        }) {
            assertEquals(HttpStatusCode.OK, status)
        }

        // Paused stream - timeout
        socket {
            outputStream.writePacket(
                RequestResponseBuilder().apply {
                    requestLine(HttpMethod.Post, "/echo", "HTTP/1.1")
                    headerLine("Host", "localhost:$port")
                    headerLine("Content-Length", "25") // <-- greater than actual body
                    emptyLine()
                    bytes(requestMessage)
                }.build()
            )
            outputStream.flush()

            val response = inputStream.bufferedReader().readText()
            assertEquals("HTTP/1.1 500 Server Error", response.lines().first())
        }
    }

    @Test
    fun idleTimeoutResponseWriter(): Unit = runTest {
        val responseMessage = "Hello, world!".toByteArray()
        val writeError = CompletableDeferred<Exception>()

        createAndStartServer {
            get("/hello") {
                call.respondBytesWriter {
                    try {
                        while (true) {
                            writeByteArray(responseMessage)
                            flush()
                            yield()
                        }
                    } catch (cause: Exception) {
                        writeError.complete(cause)
                    }
                }
            }
        }

        // Too long reading response -> timeout
        socket {
            // use a very small buffer so the server blocks on writing
            sendBufferSize = 1
            receiveBufferSize = 1

            outputStream.writePacket(
                RequestResponseBuilder().apply {
                    requestLine(HttpMethod.Get, "/hello", "HTTP/1.1")
                    headerLine("Host", "localhost:$port")
                    emptyLine()
                }.build()
            )
            outputStream.flush()

            val response = StringBuilder()
            inputStream.reader().let { reader ->
                var ch = reader.read()
                // stop after "Hello"
                while (ch != ','.code) {
                    response.append(ch.toChar())
                    ch = reader.read()
                }
            }
            assertTrue { response.endsWith("Hello") }
            assertIs<Exception>(writeError.await())
            writeError.await().printStackTrace()
        }
    }

    @Test
    fun idleTimeoutCancelsCoroutineContext(): Unit = runTest {
        val requestCancelled = CompletableDeferred<CancellationException>()

        createAndStartServer {
            get("/slow") {
                try {
                    // Server processing takes longer than idle timeout
                    delay(1000.milliseconds)
                } catch (e: CancellationException) {
                    // User code should never typically catch a CancellationException,
                    // without rethrowing as it interferes with structured concurrency.
                    requestCancelled.complete(e)
                    throw e
                }
            }
        }

        withUrl("/slow") {
            assertEquals(HttpStatusCode.InternalServerError, status)
        }

        withTimeout(5.seconds) {
            assertInstanceOf<TimeoutException>(requestCancelled.await().unwrapRootCause())
        }
    }
}

/**
 * Structured concurrency propagates cancellation through the job hierarchy, adding a wrapper at each level. In this
 * case, there are consistently two levels to unwrap.
 */
private fun CancellationException.unwrapRootCause() = cause?.cause
