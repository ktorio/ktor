/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.curl.test

import io.ktor.client.*
import io.ktor.client.engine.curl.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlinx.io.readByteArray
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class CurlResponseBodyTest {

    // Exactly the channel's buffer size: the body fills the channel as its last byte arrives,
    // so the transfer completes while the channel is still full.
    private val body = ByteArray(1024 * 1024) { (it % 251).toByte() }

    @Test
    fun `body not read before the transfer completes is kept whole`() = runTestWithRealTime {
        val head = "HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n"
        withRawServer(head.encodeToByteArray() + body) { url, sent ->
            HttpClient(Curl).use { client ->
                val received = client.prepareGet(url).execute { response ->
                    sent.await()
                    // Give the engine time to complete the transfer before reading anything
                    delay(2.seconds)
                    response.bodyAsChannel().readBuffer().readByteArray()
                }
                assertContentEquals(body, received)
            }
        }
    }

    @Test
    fun `body cut short by a connection error fails`() = runTestWithRealTime {
        // One chunk with the whole body and no terminating chunk, then the connection is closed
        val head = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n" +
            "${body.size.toString(16)}\r\n"
        withRawServer(head.encodeToByteArray() + body + "\r\n".encodeToByteArray()) { url, sent ->
            HttpClient(Curl).use { client ->
                client.prepareGet(url).execute { response ->
                    sent.await()
                    delay(2.seconds)
                    assertFails {
                        response.bodyAsChannel().readBuffer()
                    }
                }
            }
        }
    }

    private suspend fun CoroutineScope.withRawServer(
        response: ByteArray,
        block: suspend (url: String, sent: CompletableDeferred<Unit>) -> Unit
    ) {
        val sent = CompletableDeferred<Unit>()
        SelectorManager(Dispatchers.Default).use { selector ->
            aSocket(selector).tcp().bind("127.0.0.1", 0).use { server ->
                val serving = launch {
                    server.accept().use { socket ->
                        val input = socket.openReadChannel()
                        while (input.readLine()?.isNotEmpty() == true) {
                            // Skip the request head
                        }
                        val output = socket.openWriteChannel()
                        output.writeFully(response)
                        output.flushAndClose()
                        sent.complete(Unit)
                    }
                }
                try {
                    block("http://127.0.0.1:${server.localAddress.port()}/", sent)
                } finally {
                    serving.cancelAndJoin()
                }
            }
        }
    }
}
