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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.milliseconds

class JettyIdleTimeoutTest : EngineTestBase<JettyApplicationEngine, JettyApplicationEngineBase.Configuration>(Jetty) {

    override fun configure(configuration: JettyApplicationEngineBase.Configuration) {
        super.configure(configuration)
        configuration.idleTimeout = 100.milliseconds
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
                } catch (_: TimeoutCancellationException) {
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
        var writeError = CompletableDeferred<Exception>()

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
        }
    }
}
