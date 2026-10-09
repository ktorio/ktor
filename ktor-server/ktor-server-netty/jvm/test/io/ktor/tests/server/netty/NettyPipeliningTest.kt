/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class NettyPipeliningTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
    }

    @Test
    fun `completed pipelined response is flushed without waiting for a later request to finish`() = runTest {
        val slowRequestStarted = CompletableDeferred<Unit>()
        val releaseSlowResponse = CompletableDeferred<Unit>()

        val server = embeddedServer(
            Netty,
            module = {
                routing {
                    get("/fast") {
                        call.respondText("fast-response")
                    }
                    get("/slow") {
                        slowRequestStarted.complete(Unit)
                        releaseSlowResponse.await()
                        call.respondText("slow-response")
                    }
                }
            },
            configure = {
                connector {
                    port = this@NettyPipeliningTest.port
                    host = TEST_SERVER_HOST
                }
            }
        )
        server.start(wait = false)

        try {
            SelectorManager().use { selector ->
                aSocket(selector).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    // Pipeline both requests on the same connection without waiting for a response to either.
                    writeChannel.writeStringUtf8(pipelinedRequest("/fast") + pipelinedRequest("/slow"))
                    writeChannel.flush()

                    // Confirm /slow is genuinely still in flight -- its handler is suspended independently
                    // of /fast, which was already first in line and had nothing left to compute.
                    withTimeout(5.seconds) { slowRequestStarted.await() }

                    // /fast has no ordering dependency left to satisfy: it must reach the client promptly
                    // instead of being held back by the still-pending /slow request sharing the connection.
                    val fastResponse = withTimeout(2.seconds) { readChannel.readHttpResponse() }
                    assertTrue(fastResponse.contains("fast-response"), "Expected fast response, got:\n$fastResponse")

                    releaseSlowResponse.complete(Unit)

                    val slowResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(slowResponse.contains("slow-response"), "Expected slow response, got:\n$slowResponse")
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `pipelined burst does not exceed running limit even when decoded in a single read`() = runTest {
        val firstRequestStarted = CompletableDeferred<Unit>()
        val secondRequestStarted = CompletableDeferred<Unit>()
        val releaseFirstResponse = CompletableDeferred<Unit>()

        val server = embeddedServer(
            Netty,
            module = {
                routing {
                    get("/first") {
                        firstRequestStarted.complete(Unit)
                        releaseFirstResponse.await()
                        call.respondText("first-response")
                    }
                    get("/second") {
                        secondRequestStarted.complete(Unit)
                        call.respondText("second-response")
                    }
                }
            },
            configure = {
                runningLimit = 1
                connector {
                    port = this@NettyPipeliningTest.port
                    host = TEST_SERVER_HOST
                }
            }
        )
        server.start(wait = false)

        try {
            SelectorManager().use { selector ->
                aSocket(selector).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    // Write both requests before the server has read anything, so Netty's HTTP codec
                    // decodes and delivers both HttpRequest messages from a single physical socket read,
                    // in one dispatch batch, before the running-limit check ever runs.
                    writeChannel.writeStringUtf8(pipelinedRequest("/first") + pipelinedRequest("/second"))
                    writeChannel.flush()

                    withTimeout(5.seconds) { firstRequestStarted.await() }

                    // With runningLimit = 1, /second must stay queued until /first completes, even though
                    // both requests were already decoded together in the same read.
                    delay(200.milliseconds)
                    assertFalse(secondRequestStarted.isCompleted, "second request started before first completed")

                    releaseFirstResponse.complete(Unit)

                    val firstResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(
                        firstResponse.contains("first-response"),
                        "Expected first response, got:\n$firstResponse"
                    )

                    withTimeout(5.seconds) { secondRequestStarted.await() }
                    val secondResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(
                        secondResponse.contains("second-response"),
                        "Expected second response, got:\n$secondResponse"
                    )
                }
            }
        } finally {
            server.stop()
        }
    }

    @Test
    fun `keep-alive connection with runningLimit 1 serves a second sequential request`() = runTest {
        val server = embeddedServer(
            Netty,
            module = {
                routing {
                    get("/") {
                        call.respondText("response")
                    }
                }
            },
            configure = {
                runningLimit = 1
                connector {
                    port = this@NettyPipeliningTest.port
                    host = TEST_SERVER_HOST
                }
            }
        )
        server.start(wait = false)

        try {
            SelectorManager().use { selector ->
                aSocket(selector).tcp().connect(TEST_SERVER_HOST, port).use { socket ->
                    val writeChannel = socket.openWriteChannel()
                    val readChannel = socket.openReadChannel()

                    // First request must complete normally to increment activeRequests
                    writeChannel.writeStringUtf8(pipelinedRequest("/"))
                    writeChannel.flush()
                    val firstResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(firstResponse.contains("response"), "Expected response, got:\n$firstResponse")

                    // The second request must wait for first to finish
                    writeChannel.writeStringUtf8(pipelinedRequest("/"))
                    writeChannel.flush()
                    val secondResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(secondResponse.contains("response"), "Expected response, got:\n$secondResponse")
                }
            }
        } finally {
            server.stop()
        }
    }

    private fun pipelinedRequest(path: String): String =
        "GET $path HTTP/1.1\r\nHost: $TEST_SERVER_HOST\r\nConnection: keep-alive\r\n\r\n"

    private suspend fun ByteReadChannel.readHttpResponse(): String {
        val builder = StringBuilder()
        var contentLength = 0
        while (true) {
            val line = readLine() ?: error("Unexpected end of stream while reading response headers")
            builder.append(line).append("\r\n")
            if (line.isEmpty()) break

            val separator = line.indexOf(':')
            if (separator > 0 && line.take(separator).equals(HttpHeaders.ContentLength, ignoreCase = true)) {
                contentLength = line.substring(separator + 1).trim().toInt()
            }
        }
        if (contentLength > 0) {
            val body = ByteArray(contentLength)
            readFully(body)
            builder.append(body.decodeToString())
        }
        return builder.toString()
    }
}
