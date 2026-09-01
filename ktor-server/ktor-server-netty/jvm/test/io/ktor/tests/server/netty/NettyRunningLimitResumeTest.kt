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

class NettyRunningLimitResumeTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    companion object {
        private const val TEST_SERVER_HOST = "127.0.0.1"
    }

    @Test
    fun `reading resumes once in-flight requests drop back below the running limit`() = runTest {
        val firstRequestStarted = CompletableDeferred<Unit>()
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
                        call.respondText("second-response")
                    }
                }
            },
            configure = {
                runningLimit = 1
                connector {
                    port = this@NettyRunningLimitResumeTest.port
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

                    // Send the first request alone so the server genuinely reads and starts handling it
                    // (rather than decoding both requests out of one buffered chunk).
                    writeChannel.writeStringUtf8(pipelinedRequest("/first"))
                    writeChannel.flush()
                    withTimeout(5.seconds) { firstRequestStarted.await() }

                    // With runningLimit = 1, the server already stopped arming further socket reads once
                    // /first was accepted. This second request now sits unread in the socket buffer until
                    // /first completes and the engine resumes reading.
                    writeChannel.writeStringUtf8(pipelinedRequest("/second"))
                    writeChannel.flush()

                    releaseFirstResponse.complete(Unit)

                    val firstResponse = withTimeout(5.seconds) { readChannel.readHttpResponse() }
                    assertTrue(
                        firstResponse.contains("first-response"),
                        "Expected first response, got:\n$firstResponse"
                    )

                    // This read hangs forever if the engine never resumes reading past the running limit.
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
