/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.cio

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.test.base.*
import io.ktor.http.*
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import kotlin.test.*
import kotlin.time.Duration.Companion.seconds

class CIOEngineDnsTest : ClientEngineTest<CIOEngineConfig>(CIO) {

    @Test
    fun testDnsResolverIsInvokedAndUsedForConnection() = testClient {
        val resolvedHosts = mutableListOf<String>()
        config {
            engine {
                dnsResolver = { hostname ->
                    resolvedHosts += hostname
                    listOf(TEST_SERVER_SOCKET_HOST)
                }
            }
        }

        withServerSocket { client, socket ->
            launch {
                val serverPort = (socket.localAddress as InetSocketAddress).port
                client.get { url(host = "example.invalid", port = serverPort, path = "/") }
            }

            socket.accept().use {
                val readChannel = it.openReadChannel()
                val writeChannel = it.openWriteChannel()
                readAvailableLines(readChannel)
                writeOkResponse(writeChannel)
            }

            assertEquals(listOf("example.invalid"), resolvedHosts)
        }
    }

    @Test
    fun testDnsResolverEmptyResultFailsConnection() = testClient {
        config {
            engine {
                dnsResolver = { emptyList() }
            }
        }

        test { client ->
            assertFailsWith<FailToConnectException> {
                client.get { url(host = "example.invalid", port = 0, path = "/") }
            }
        }
    }

    @Test
    fun testDnsResolverThrowingDoesNotLeakConnectionSlot() = testClient {
        var attempts = 0
        config {
            engine {
                pipelining = true
                endpoint { maxConnectionsPerRoute = 1 }
                dnsResolver = {
                    attempts++
                    if (attempts == 1) error("simulated DNS failure")
                    listOf(TEST_SERVER_SOCKET_HOST)
                }
            }
        }

        withServerSocket { client, socket ->
            val serverPort = (socket.localAddress as InetSocketAddress).port

            val firstFailure = assertFails {
                client.get { url(host = "example.invalid", port = serverPort, path = "/") }
            }
            assertContains(firstFailure.message ?: "", "simulated DNS failure")

            // With pipelining enabled and maxConnectionsPerRoute=1, a leaked counter from the
            // failed first request would block this second request forever in deliveryPoint.send,
            // because no pipeline was ever created and the per-route slot is still held.
            launch {
                socket.accept().use {
                    val readChannel = it.openReadChannel()
                    val writeChannel = it.openWriteChannel()
                    readAvailableLines(readChannel)
                    writeOkResponse(writeChannel)
                }
            }
            val response = withTimeout(5.seconds) {
                client.get { url(host = "example.invalid", port = serverPort, path = "/") }
            }
            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    private suspend fun readAvailableLine(channel: ByteReadChannel): String {
        val buffer = ByteArray(1024)
        val length = channel.readAvailable(buffer)
        return buffer.decodeToString(0, 0 + length)
    }

    private suspend fun readAvailableLines(channel: ByteReadChannel): List<String> {
        return readAvailableLine(channel).split("\r\n")
    }

    private suspend fun writeOkResponse(channel: ByteWriteChannel) {
        channel.apply {
            writeStringUtf8("HTTP/1.1 200 Ok\r\n")
            writeStringUtf8("Content-Length: 0\r\n")
            writeStringUtf8("\r\n")
            flush()
        }
    }

    private fun TestClientBuilder<*>.withServerSocket(
        block: suspend CoroutineScope.(HttpClient, ServerSocket) -> Unit,
    ) = test { client ->
        val selectorManager = SelectorManager()
        selectorManager.use {
            aSocket(it).tcp().bind(TEST_SERVER_SOCKET_HOST, 0).use { socket ->
                coroutineScope {
                    block(client, socket)
                }
            }
        }
    }

    companion object {
        private const val TEST_SERVER_SOCKET_HOST = "127.0.0.1"
    }
}
