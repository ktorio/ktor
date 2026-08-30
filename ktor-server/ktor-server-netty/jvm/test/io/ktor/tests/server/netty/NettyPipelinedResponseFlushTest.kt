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
import kotlin.time.Duration.Companion.seconds

class NettyPipelinedResponseFlushTest :
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
                    port = this@NettyPipelinedResponseFlushTest.port
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
