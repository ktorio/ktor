/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.test.base.*
import io.ktor.utils.io.*
import io.netty.channel.ChannelOption
import io.netty.handler.timeout.WriteTimeoutException
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.net.Socket
import java.net.SocketException
import kotlin.test.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Checks what happens when `responseWriteTimeoutSeconds` expires because the client stopped reading:
 * the connection is closed, the timeout is logged, and the active call is cancelled with it as the cause.
 *
 * The client deliberately uses a blocking [Socket]: ktor-network sockets drain the OS buffer into an
 * in-memory channel regardless of the consumer, which would hide the backpressure these tests rely on.
 */
class NettyResponseWriteTimeoutTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    companion object {
        private const val SUCCESS_RESPONSE = "HTTP/1.1 200"
        private const val SOCKET_BUFFER_SIZE = 8 * 1024
        private const val READ_CHUNK_SIZE = 32 * 1024
        private const val BYTES_SIZE = 1024 * 1024
        private const val SMALL_SIZE = 1024
    }

    private var writeTimeoutSeconds = 1
    private var h2c = false
    private val endlessWriterFailure = CompletableDeferred<Throwable>()

    init {
        enableSsl = false
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.responseWriteTimeoutSeconds = writeTimeoutSeconds
        configuration.enableH2c = h2c
        // Keep the kernel send buffer small so the result doesn't depend on OS autotuning
        configuration.configureBootstrap = {
            childOption(ChannelOption.SO_SNDBUF, SOCKET_BUFFER_SIZE)
        }
    }

    @Test
    fun `stalled client is disconnected`() = runTest {
        startServer()

        val response = slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

        assertTrue(
            response.bodySize < BYTES_SIZE,
            "Expected the stalled connection to be closed, but received all ${response.bodySize} bytes"
        )
    }

    @Test
    fun `stalled client is disconnected through h2c fallback pipeline`() = runTest {
        h2c = true
        startServer()

        val response = slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

        assertTrue(
            response.bodySize < BYTES_SIZE,
            "Expected the stalled connection to be closed, but received all ${response.bodySize} bytes"
        )
    }

    @Test
    fun `active call is cancelled with the write timeout as cause`() = runTest {
        startServer()

        socket {
            receiveBufferSize = SOCKET_BUFFER_SIZE
            sendGet("/endless", keepAlive = false)
            inputStream.readHeaders()
            // Stop reading, so the server's writes back up until the write timeout fires

            val cause = withTimeout(10.seconds) { endlessWriterFailure.await() }

            assertTrue(
                generateSequence(cause) { it.cause }.any { it is WriteTimeoutException },
                "Expected the call to be cancelled by WriteTimeoutException, but got: $cause"
            )
        }
    }

    @Test
    fun `stalled client disconnect is logged`() = runTest {
        val logger = LoggerFactory.getLogger("io.ktor.tests.server.netty.WriteTimeoutLogging") as Logger
        val previousLevel = logger.level
        val logEvents = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = Level.DEBUG
        logger.addAppender(logEvents)

        try {
            startServer(log = logger)

            slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

            assertTrue(
                logEvents.list.any {
                    it.level == Level.DEBUG && it.formattedMessage.startsWith("Response write timed out")
                },
                "Expected a write timeout log entry, but got: ${logEvents.list.map { it.formattedMessage }}"
            )
        } finally {
            logger.detachAppender(logEvents)
            logger.level = previousLevel
        }
    }

    @Test
    fun `stalled client is not disconnected when timeout is disabled`() = runTest {
        writeTimeoutSeconds = 0
        startServer()

        val response = slowGet("/bytes", pause = 3.seconds, stallAfterFirstChunk = true)

        assertCompleteResponse(response)
    }

    /**
     * A keep-alive connection that is simply waiting for the client's next request should stay open.
     */
    @Test
    fun `idle keep-alive connection is not closed by write timeout`() = runTest {
        startServer()

        socket {
            sendGet("/small", keepAlive = true)
            assertEquals(SMALL_SIZE, inputStream.readHeaders().contentLength)
            inputStream.readUpTo(ByteArray(SMALL_SIZE))

            delay(3.seconds)

            sendGet("/small", keepAlive = true)
            val headers = inputStream.readHeaders()
            assertTrue(headers.statusLine.startsWith(SUCCESS_RESPONSE), "Unexpected status: ${headers.statusLine}")
            assertEquals(SMALL_SIZE, headers.contentLength)
        }
    }

    private suspend fun startServer(log: Logger? = null) {
        createAndStartServer(log) {
            get("/bytes") {
                call.respondBytes(ByteArray(BYTES_SIZE) { it.toByte() })
            }
            get("/endless") {
                call.respondBytesWriter {
                    try {
                        val chunk = ByteArray(8 * 1024)
                        while (true) {
                            writeFully(chunk)
                            flush()
                        }
                    } catch (cause: Throwable) {
                        endlessWriterFailure.complete(cause)
                        throw cause
                    }
                }
            }
            get("/small") {
                call.respondBytes(ByteArray(SMALL_SIZE))
            }
        }
    }

    private fun Socket.sendGet(path: String, keepAlive: Boolean) {
        val connection = if (keepAlive) "keep-alive" else "close"
        outputStream.write("GET $path HTTP/1.1\r\nHost: localhost\r\nConnection: $connection\r\n\r\n".toByteArray())
        outputStream.flush()
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
