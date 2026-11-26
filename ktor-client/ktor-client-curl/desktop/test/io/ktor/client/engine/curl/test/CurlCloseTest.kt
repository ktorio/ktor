/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.time.Duration.Companion.seconds

class CurlCloseTest {

    @Test
    fun `closing the client fails a request that waits for the response`() = runTest(timeout = 10.seconds) {
        withServer { port, awaitRequest ->
            val client = HttpClient(Curl)
            val request = async { runCatching { client.get("http://127.0.0.1:$port/") } }
            awaitRequest()

            client.close()

            assertIs<ClientEngineClosedException>(request.await().exceptionOrNull())
        }
    }

    @Test
    fun `closing the client fails a response body that is still streaming`() = runTest(timeout = 10.seconds) {
        withServer { port, awaitRequest ->
            val client = HttpClient(Curl)
            val body = async {
                runCatching {
                    client.prepareGet("http://127.0.0.1:$port/").execute { response ->
                        client.close()
                        response.bodyAsText()
                    }
                }
            }
            awaitRequest().apply {
                writeStringUtf8("HTTP/1.1 200 OK\r\nContent-Length: 10\r\n\r\nhello")
                flush()
            }

            val failure = assertIs<ClosedByteChannelException>(body.await().exceptionOrNull())
            assertIs<ClientEngineClosedException>(failure.cause)
        }
    }

    private suspend fun withServer(
        block: suspend (port: Int, awaitRequest: suspend () -> ByteWriteChannel) -> Unit
    ) {
        SelectorManager().use { selector ->
            aSocket(selector).tcp().bind("127.0.0.1", 0).use { server ->
                val connections = mutableListOf<Socket>()
                try {
                    block((server.localAddress as InetSocketAddress).port) {
                        val connection = server.accept().also { connections += it }
                        val input = connection.openReadChannel()
                        do {
                            val line = input.readLine()
                        } while (!line.isNullOrEmpty())
                        connection.openWriteChannel()
                    }
                } finally {
                    connections.forEach { it.close() }
                }
            }
        }
    }
}
