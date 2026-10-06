/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.server.websocket.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import io.netty.channel.ChannelOption
import kotlinx.coroutines.*
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import io.ktor.client.plugins.websocket.WebSockets as ClientWebSockets

/**
 * Checks the reader, writer and all-idle timeouts of the Netty engine on HTTP/1.1 connections.
 *
 * The client deliberately uses a blocking [Socket]: ktor-network sockets drain the OS buffer into an
 * in-memory channel regardless of the consumer, which would hide the backpressure these tests rely on.
 */
class NettyIdleTimeoutTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    companion object {
        private const val SUCCESS_RESPONSE = "HTTP/1.1 200"
        private const val SOCKET_BUFFER_SIZE = 8 * 1024
        private const val READ_CHUNK_SIZE = 32 * 1024
        private const val BYTES_SIZE = 1024 * 1024
        private const val SMALL_SIZE = 1024
    }

    private var responseWriteTimeoutSeconds = 0
    private var readerIdleTimeoutSeconds = 0
    private var writerIdleTimeoutSeconds = 1
    private var allIdleTimeoutSeconds = 0
    private var h2c = false
    private val silentHandlerFinished = CompletableDeferred<Unit>()

    init {
        enableSsl = false
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.responseWriteTimeoutSeconds = responseWriteTimeoutSeconds
        configuration.readerIdleTimeoutSeconds = readerIdleTimeoutSeconds
        configuration.writerIdleTimeoutSeconds = writerIdleTimeoutSeconds
        configuration.allIdleTimeoutSeconds = allIdleTimeoutSeconds
        configuration.enableH2c = h2c
        // Keep the kernel send buffer small so the result doesn't depend on OS autotuning
        configuration.configureBootstrap = {
            childOption(ChannelOption.SO_SNDBUF, SOCKET_BUFFER_SIZE)
        }
    }

    @Test
    fun `default configuration uses the writer idle timeout instead of the write timeout`() {
        val configuration = NettyApplicationEngine.Configuration()

        assertEquals(0, configuration.responseWriteTimeoutSeconds)
        assertEquals(0, configuration.readerIdleTimeoutSeconds)
        assertEquals(10, configuration.writerIdleTimeoutSeconds)
        assertEquals(0, configuration.allIdleTimeoutSeconds)
    }

    @Test
    fun `writer idle - slow steady client receives large byte array`() = runTest {
        startServer()

        val response = slowGet("/bytes", pause = 100.milliseconds)

        assertCompleteResponse(response)
    }

    @Test
    fun `writer idle - slow steady client receives streamed body`() = runTest {
        startServer()

        val response = slowGet("/stream", pause = 100.milliseconds)

        assertCompleteResponse(response)
    }

    @Test
    fun `writer idle - slow steady client receives byte array through h2c fallback pipeline`() = runTest {
        h2c = true
        startServer()

        val response = slowGet("/bytes", pause = 100.milliseconds)

        assertCompleteResponse(response)
    }

    @Test
    fun `writer idle - stalled client is disconnected`() = runTest {
        startServer()

        val response = slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

        assertTrue(
            response.bodySize < BYTES_SIZE,
            "Expected the stalled connection to be closed, but received all ${response.bodySize} bytes"
        )
    }

    @Test
    fun `writer idle - stalled client is not disconnected when disabled`() = runTest {
        writerIdleTimeoutSeconds = 0
        startServer()

        val response = slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

        assertCompleteResponse(response)
    }

    @Test
    fun `writer idle - idle keep-alive connection stays open`() = runTest {
        startServer()

        socket {
            requestSmall()
            delay(3.seconds)

            assertSmallResponse()
        }
    }

    @Test
    fun `explicit response write timeout still cuts off a slow client mid-response`() = runTest {
        responseWriteTimeoutSeconds = 1
        writerIdleTimeoutSeconds = 0
        startServer()

        // The byte array is sent as one write, which takes longer than the per-write deadline at this rate
        val response = slowGet("/bytes", pause = 100.milliseconds)

        assertTrue(
            response.bodySize < BYTES_SIZE,
            "Expected the per-write deadline to close the connection, but received all ${response.bodySize} bytes"
        )
    }

    @Test
    fun `reader idle - idle keep-alive connection is closed`() = runTest {
        readerIdleTimeoutSeconds = 1
        startServer()

        socket {
            requestSmall()
            delay(3.seconds)

            assertClosedByServer()
        }
    }

    @Test
    fun `reader idle - request headers that stop arriving are closed`() = runTest {
        readerIdleTimeoutSeconds = 1
        startServer()

        socket {
            // No blank line ends the headers, so no call is started and the connection only waits for more data
            outputStream.write("GET /small HTTP/1.1\r\nHost: localhost\r\n".toByteArray())
            outputStream.flush()
            delay(3.seconds)

            assertClosedByServer()
        }
    }

    @Test
    fun `reader idle - long response to a client that sends nothing is not cut off`() = runTest {
        readerIdleTimeoutSeconds = 1
        startServer()

        val response = slowGet("/bytes", pause = 100.milliseconds)

        assertCompleteResponse(response)
    }

    @Test
    fun `all idle - idle keep-alive connection is closed`() = runTest {
        allIdleTimeoutSeconds = 1
        startServer()

        socket {
            requestSmall()
            delay(3.seconds)

            assertClosedByServer()
        }
    }

    @Test
    fun `all idle - slow steady client receives large byte array`() = runTest {
        allIdleTimeoutSeconds = 1
        startServer()

        val response = slowGet("/bytes", pause = 100.milliseconds)

        assertCompleteResponse(response)
    }

    @Test
    fun `all idle - handler that sends nothing for longer than the timeout is disconnected`() = runTest {
        allIdleTimeoutSeconds = 1
        startServer()

        socket {
            sendGet("/silent", keepAlive = true)

            assertClosedByServer()
        }
        // Let the handler finish, so stopping the server doesn't cancel it mid-call
        silentHandlerFinished.await()
    }

    @Test
    fun `h2c fallback pipeline works with all idle timeouts disabled`() = runTest {
        h2c = true
        writerIdleTimeoutSeconds = 0
        startServer()

        socket {
            assertSmallResponse()
        }
    }

    @Test
    fun `all idle - slow request upload through h2c fallback pipeline is not cut off`() = runTest {
        h2c = true
        allIdleTimeoutSeconds = 1
        startServer()

        socket {
            val chunk = ByteArray(1024)
            val chunks = 10
            outputStream.write(
                "POST /upload HTTP/1.1\r\nHost: localhost\r\nContent-Length: ${chunk.size * chunks}\r\n\r\n"
                    .toByteArray()
            )
            // Nothing is written back while the body arrives, so only the reads keep the connection active
            repeat(chunks) {
                outputStream.write(chunk)
                outputStream.flush()
                delay(300.milliseconds)
            }

            val headers = inputStream.readHeaders()
            assertTrue(headers.statusLine.startsWith(SUCCESS_RESPONSE), "Unexpected status: ${headers.statusLine}")
        }
    }

    @Test
    fun `reader idle - handler that runs longer than the timeout is not cut off`() = runTest {
        readerIdleTimeoutSeconds = 1
        startServer()

        socket {
            sendGet("/silent", keepAlive = true)

            val headers = inputStream.readHeaders()
            assertTrue(headers.statusLine.startsWith(SUCCESS_RESPONSE), "Unexpected status: ${headers.statusLine}")
            assertEquals(SMALL_SIZE, headers.contentLength)
        }
    }

    @Test
    fun `reader idle - quiet WebSocket stays open`() = runTest {
        readerIdleTimeoutSeconds = 1
        startServer()

        echoWebSocket {
            assertEcho("a")

            // The upgraded call is still active, so a quiet WebSocket is not an idle connection
            delay(3.seconds)

            assertEcho("b")
        }
    }

    @Test
    fun `all idle - quiet WebSocket is closed`() = runTest {
        allIdleTimeoutSeconds = 1
        startServer()

        echoWebSocket {
            assertEcho("a")

            // Nothing is read or written, so the connection is idle even though the session is still running
            delay(3.seconds)

            assertFails { assertEcho("b") }
        }
    }

    private suspend fun startServer() {
        createAndStartServer {
            application.install(WebSockets)
            webSocket("/ws") {
                for (frame in incoming) {
                    if (frame is Frame.Text) send(Frame.Text("echo:" + frame.readText()))
                }
            }
            post("/upload") {
                call.receiveChannel().discard()
                call.respondText("uploaded")
            }
            get("/bytes") {
                call.respondBytes(ByteArray(BYTES_SIZE) { it.toByte() })
            }
            get("/stream") {
                call.respondOutputStream(contentLength = BYTES_SIZE.toLong()) {
                    val chunk = ByteArray(8 * 1024)
                    repeat(BYTES_SIZE / chunk.size) { write(chunk) }
                }
            }
            get("/small") {
                call.respondBytes(ByteArray(SMALL_SIZE))
            }
            get("/silent") {
                try {
                    delay(2.seconds)
                    call.respondBytes(ByteArray(SMALL_SIZE))
                } finally {
                    silentHandlerFinished.complete(Unit)
                }
            }
        }
    }

    private suspend fun echoWebSocket(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        HttpClient(CIO) { install(ClientWebSockets) }.use { client ->
            client.webSocket(host = "127.0.0.1", port = port, path = "/ws", block = block)
        }
    }

    private suspend fun DefaultClientWebSocketSession.assertEcho(text: String) {
        send(Frame.Text(text))
        val echo = withTimeout(3.seconds) { incoming.receive() }
        assertEquals("echo:$text", (echo as Frame.Text).readText())
    }

    private fun Socket.sendGet(path: String, keepAlive: Boolean) {
        val connection = if (keepAlive) "keep-alive" else "close"
        outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: $connection\r\n\r\n".toByteArray())
        outputStream.flush()
    }

    private fun Socket.requestSmall() {
        sendGet("/small", keepAlive = true)
        assertEquals(SMALL_SIZE, inputStream.readHeaders().contentLength)
        inputStream.readUpTo(ByteArray(SMALL_SIZE))
    }

    private fun Socket.assertSmallResponse() {
        sendGet("/small", keepAlive = true)
        val headers = inputStream.readHeaders()
        assertTrue(headers.statusLine.startsWith(SUCCESS_RESPONSE), "Unexpected status: ${headers.statusLine}")
        assertEquals(SMALL_SIZE, headers.contentLength)
    }

    private fun Socket.assertClosedByServer() {
        val read = try {
            inputStream.read()
        } catch (_: SocketException) {
            -1 // connection reset by the server
        }
        assertEquals(-1, read, "Expected the server to close the connection")
    }

    /**
     * Reads the response in [READ_CHUNK_SIZE] pieces, pausing for [pause] between pieces
     * (or only once, after the first piece, if [stallAfterFirstChunk]), until the server closes the connection.
     */
    private suspend fun slowGet(path: String, pause: Duration, stallAfterFirstChunk: Boolean = false): Response {
        lateinit var response: Response
        socket {
            receiveBufferSize = SOCKET_BUFFER_SIZE
            sendGet(path, keepAlive = false)
            val headers = inputStream.readHeaders()

            val buffer = ByteArray(READ_CHUNK_SIZE)
            var bodySize = 0
            while (true) {
                val read = try {
                    inputStream.readUpTo(buffer)
                } catch (_: SocketException) {
                    break // connection reset by the server
                }
                if (read <= 0) break
                if (!stallAfterFirstChunk || bodySize == 0) delay(pause)
                bodySize += read
            }
            response = Response(headers, bodySize)
        }
        return response
    }

    private fun assertCompleteResponse(response: Response) {
        assertTrue(
            response.headers.statusLine.startsWith(SUCCESS_RESPONSE),
            "Unexpected status: ${response.headers.statusLine}"
        )
        assertEquals(BYTES_SIZE, response.headers.contentLength)
        assertEquals(BYTES_SIZE, response.bodySize, "Connection was closed before the whole body was received")
    }

    /**
     * Fills [buffer] unless the stream ends first; returns the number of bytes read, or -1 at end of stream.
     */
    private fun InputStream.readUpTo(buffer: ByteArray): Int {
        var total = 0
        while (total < buffer.size) {
            val read = read(buffer, total, buffer.size - total)
            if (read < 0) return if (total == 0) -1 else total
            total += read
        }
        return total
    }

    private fun InputStream.readHeaders(): Headers {
        val headers = StringBuilder()
        while (!headers.endsWith("\r\n\r\n")) {
            val byte = read()
            check(byte >= 0) { "Connection closed while reading headers: $headers" }
            headers.append(byte.toChar())
        }
        val lines = headers.trim().lines()
        val contentLength = lines.drop(1)
            .map { it.split(":", limit = 2) }
            .firstOrNull { it[0].trim().equals("Content-Length", ignoreCase = true) }
            ?.get(1)?.trim()?.toInt()
        return Headers(lines.first(), contentLength)
    }

    private class Headers(val statusLine: String, val contentLength: Int?)

    private class Response(val headers: Headers, val bodySize: Int)
}
