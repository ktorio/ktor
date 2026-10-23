/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.java

import io.ktor.client.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.test.dispatcher.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import org.junit.jupiter.api.condition.EnabledForJreRange
import org.junit.jupiter.api.condition.JRE
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.SocketTimeoutException
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies that cancelling a request aborts the underlying HTTP exchange,
 * so the server observes the connection being closed.
 */
@EnabledForJreRange(
    min = JRE.JAVA_16,
    disabledReason = "HttpClient futures are cancelable since JDK 16 (JDK-8245462)"
)
class CancellationTest {

    @Test
    fun `cancelling a request before response headers closes the connection`() = runTestWithRealTime {
        SilentServer().use { server ->
            HttpClient(Java).use { client ->
                val request = launch { client.get(server.url) }

                server.acceptRequest().use { connection ->
                    request.cancelAndJoin()
                    connection.assertClosedByClient()
                }
            }
        }
    }

    @Test
    fun `cancelling a request after its body is sent closes the connection`() = runTestWithRealTime {
        SilentServer().use { server ->
            HttpClient(Java).use { client ->
                val request = launch {
                    client.post(server.url) { setBody("request body") }
                }

                server.acceptRequest().use { connection ->
                    request.cancelAndJoin()
                    connection.assertClosedByClient()
                }
            }
        }
    }

    @Test
    fun `cancelling a request while reading the response body closes the connection`() = runTestWithRealTime {
        SilentServer().use { server ->
            HttpClient(Java).use { client ->
                val bodyStarted = CompletableDeferred<Unit>()
                val request = launch {
                    client.prepareGet(server.url).execute { response ->
                        val body = response.bodyAsChannel()
                        body.readByte()
                        bodyStarted.complete(Unit)
                        body.readRemaining()
                    }
                }

                server.acceptRequest().use { connection ->
                    connection.write("HTTP/1.1 200 OK\r\nContent-Length: 1024\r\n\r\nx")
                    bodyStarted.await()
                    request.cancelAndJoin()
                    connection.assertClosedByClient()
                }
            }
        }
    }

    @Test
    fun `cancelled requests do not leave connections open`() = runTestWithRealTime {
        SilentServer().use { server ->
            HttpClient(Java).use { client ->
                val connections = List(10) {
                    val request = launch { client.get(server.url) }
                    server.acceptRequest().also { request.cancelAndJoin() }
                }

                try {
                    connections.forEach { it.assertClosedByClient() }
                } finally {
                    connections.forEach { it.close() }
                }
            }
        }
    }

    @Test
    fun `cancelling a WebSocket handshake closes the connection`() = runTestWithRealTime {
        SilentServer().use { server ->
            HttpClient(Java) { install(WebSockets) }.use { client ->
                val session = launch { client.webSocket(server.url.replace("http", "ws")) {} }

                server.acceptRequest().use { connection ->
                    session.cancelAndJoin()
                    connection.assertClosedByClient()
                }
            }
        }
    }
}

private const val SOCKET_TIMEOUT_MILLIS = 5000

/**
 * An HTTP/1.1 server that reads requests and responds only with what the test writes.
 */
private class SilentServer : Closeable {
    private val serverSocket = ServerSocket(0, 0, InetAddress.getByName("127.0.0.1")).apply {
        soTimeout = SOCKET_TIMEOUT_MILLIS
    }

    val url = "http://127.0.0.1:${serverSocket.localPort}/"

    /**
     * Accepts a connection and reads a request from it, including its body.
     */
    suspend fun acceptRequest(): Connection = runInterruptible(Dispatchers.IO) {
        Connection(serverSocket.accept()).apply { readRequest() }
    }

    override fun close() {
        serverSocket.close()
    }
}

private class Connection(private val socket: Socket) : Closeable {
    private val input = socket.getInputStream().buffered()
    private val output = socket.getOutputStream()

    init {
        socket.soTimeout = SOCKET_TIMEOUT_MILLIS
    }

    fun readRequest() {
        val head = StringBuilder()
        while (!head.endsWith("\r\n\r\n")) {
            val byte = input.read()
            check(byte != -1) { "Connection closed before the request head was read: $head" }
            head.append(byte.toChar())
        }

        val contentLength = head.lineSequence()
            .firstOrNull { it.startsWith("Content-Length:", ignoreCase = true) }
            ?.substringAfter(':')?.trim()?.toInt()
            ?: 0
        repeat(contentLength) {
            check(input.read() != -1) { "Connection closed before the request body was read" }
        }
    }

    fun write(text: String) {
        output.write(text.toByteArray())
        output.flush()
    }

    suspend fun assertClosedByClient() {
        val closed = runInterruptible(Dispatchers.IO) { isClosedByClient() }
        assertTrue(closed, "The client didn't close the connection within $SOCKET_TIMEOUT_MILLIS ms")
    }

    private fun isClosedByClient(): Boolean {
        return try {
            while (input.read() != -1) {
                // Skip anything the client sends before closing the connection
            }
            true
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: SocketException) {
            // Connection reset by the client
            true
        }
    }

    override fun close() {
        socket.close()
    }
}
