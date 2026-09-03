/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.readAvailable
import io.netty.handler.codec.quic.QuicException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3StreamsTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    /**
     * Interleaves streams of very different sizes on one connection and checks each response body
     * is exactly its own payload — a body assembled from another stream's DATA frames would show up
     * as a wrong character or a wrong length.
     */
    @Test
    fun `concurrent streams on one connection do not cross talk`() = runTest {
        createAndStartServer {
            get("/payload/{mark}") {
                val mark = call.parameters["mark"]!!
                val size = call.request.queryParameters["size"]!!.toInt()
                call.respondText(mark.repeat(size))
            }
        }

        // Mixed sizes so small responses complete while large ones are still streaming.
        val payloads = listOf(
            "a" to 1,
            "b" to 100_000,
            "c" to 10,
            "d" to 250_000,
            "e" to 1_000,
            "f" to 50_000,
        )

        withHttp3Client(sslPort) { connection ->
            // Open every stream and send its request before reading any response, so they really
            // are in flight at the same time.
            val streams = payloads.map { (mark, size) ->
                connection.openStream().also { stream ->
                    stream.sendHeaders(path = "/payload/$mark?size=$size", endStream = true)
                }
            }

            streams.zip(payloads).forEach { (stream, payload) ->
                val (mark, size) = payload
                val body = stream.awaitResponse(timeout = 30.seconds).bodyText
                assertEquals(size, body.length, "stream '$mark' has the wrong body length")
                assertEquals(mark.repeat(size), body, "stream '$mark' received another stream's data")
            }
        }
    }

    /**
     * Six requests in flight on one connection, under the default limit of 100 concurrent
     * bidirectional streams. The configured limit itself is exercised by
     * [NettyHttp3SmallWindowTest].
     */
    @Test
    fun `several concurrent requests on one connection all complete`() = runTest {
        createAndStartServer {
            get("/slow/{index}") {
                delay(200) // hold each stream open so the limit is genuinely reached
                call.respondText("ok:${call.parameters["index"]}")
            }
        }

        withHttp3Client(sslPort) { connection ->
            coroutineScope {
                val responses = (1..6).map { index ->
                    async(Dispatchers.IO) {
                        connection.request(path = "/slow/$index", timeout = 30.seconds).bodyText
                    }
                }.awaitAll()

                assertEquals((1..6).map { "ok:$it" }, responses)
            }
        }
    }

    /**
     * A body spread over many DATA frames must arrive intact when read as a channel. The
     * flow-control angle is covered by [NettyHttp3SmallWindowTest], which shrinks the windows so
     * this much data has to cross them repeatedly.
     */
    @Test
    fun `a multi frame request body is readable through receiveChannel`() = runTest {
        createAndStartServer {
            post("/upload") {
                val received = call.receiveChannel().countBytes()
                call.respondText(received.toString())
            }
        }

        val chunk = ByteArray(16 * 1024) { (it % 256).toByte() }
        val chunks = 32 // 512 KB across 32 DATA frames

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(method = "POST", path = "/upload")
            repeat(chunks) { stream.sendData(chunk) }
            stream.endOutput()

            val response = stream.awaitResponse(timeout = 30.seconds)
            assertEquals("200", response.status)
            assertEquals((chunk.size * chunks).toString(), response.bodyText, "request body was truncated")
        }
    }

    /** The server must reassemble a body split across many DATA frames when read as a stream. */
    @Test
    fun `a multi frame request body is readable through receiveStream`() = runTest {
        createAndStartServer {
            post("/upload") {
                val bytes = call.receiveStream().readBytes()
                call.respondText("${bytes.size}:${bytes.sumOf { it.toInt() and 0xFF }}")
            }
        }

        val chunk = ByteArray(8 * 1024) { (it % 251).toByte() }
        val chunks = 50
        val expectedSum = chunk.sumOf { it.toInt() and 0xFF }.toLong() * chunks

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(method = "POST", path = "/upload")
            repeat(chunks) { stream.sendData(chunk) }
            stream.endOutput()

            val response = stream.awaitResponse(timeout = 30.seconds)
            assertEquals("${chunk.size * chunks}:$expectedSum", response.bodyText)
        }
    }

    /**
     * A handler that is slow to start reading must not lose data: back-pressure has to hold the
     * upload until the handler drains it.
     *
     * This is the functional half of the unbounded-inbound-buffering concern — `contentActor` uses
     * an unlimited channel, so what actually bounds memory is QUIC flow control. How much is
     * retained under sustained load belongs to the Phase 3 soak run.
     */
    @Test
    fun `a slow handler still receives the whole request body`() = runTest {
        createAndStartServer {
            post("/slow-upload") {
                delay(500) // the client is already sending while the handler is not reading
                val received = call.receiveChannel().countBytes()
                call.respondText(received.toString())
            }
        }

        val chunk = ByteArray(16 * 1024) { (it % 256).toByte() }
        val chunks = 16

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(method = "POST", path = "/slow-upload")
            repeat(chunks) { stream.sendData(chunk) }
            stream.endOutput()

            val response = stream.awaitResponse(timeout = 30.seconds)
            assertEquals((chunk.size * chunks).toString(), response.bodyText)
        }
    }

    private suspend fun ByteReadChannel.countBytes(): Int {
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = readAvailable(buffer)
            if (read <= 0) break
            total += read
        }
        return total
    }
}

@OptIn(ExperimentalKtorApi::class)
class NettyHttp3SmallWindowTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3 {
            // What the server will accept before it must issue more credit.
            quicInitialMaxData = 64 * 1024
            quicInitialMaxStreamDataBidirectionalRemote = 32 * 1024
            quicInitialMaxStreamsBidirectional = 2
        }
    }

    @Test
    fun `a body many times the stream window transfers intact`() = runTest {
        createAndStartServer {
            post("/upload") {
                val bytes = call.receiveStream().readBytes()
                call.respondText(bytes.size.toString())
            }
        }

        val chunk = ByteArray(16 * 1024) { (it % 256).toByte() }
        val chunks = 40 // 640 KB against a 32 KB stream window and a 64 KB connection window

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(method = "POST", path = "/upload")
            repeat(chunks) { stream.sendData(chunk) }
            stream.endOutput()

            val response = stream.awaitResponse(timeout = 60.seconds)
            assertEquals("200", response.status)
            assertEquals((chunk.size * chunks).toString(), response.bodyText, "request body was truncated")
        }
    }

    @Test
    fun `concurrent requests up to the configured stream limit all complete`() = runTest {
        createAndStartServer {
            get("/slow/{index}") {
                delay(200)
                call.respondText("ok:${call.parameters["index"]}")
            }
        }

        withHttp3Client(sslPort) { connection ->
            coroutineScope {
                val responses = (1..STREAM_LIMIT).map { index ->
                    async(Dispatchers.IO) {
                        connection.request(path = "/slow/$index", timeout = 60.seconds).bodyText
                    }
                }.awaitAll()

                assertEquals((1..STREAM_LIMIT).map { "ok:$it" }, responses)
            }
        }
    }

    /**
     * `quicInitialMaxStreamsBidirectional` is advertised to the peer, and the peer's own QUIC stack
     * enforces it: opening one stream too many fails locally with `QUICHE_ERR_STREAM_LIMIT` without
     * anything reaching the server. The server neither errors nor drops the connection.
     */
    @Test
    fun `exceeding the advertised stream limit is refused locally`() = runTest {
        createAndStartServer {
            get("/slow/{index}") {
                delay(500)
                call.respondText("ok:${call.parameters["index"]}")
            }
        }

        withHttp3Client(sslPort) { connection ->
            // Occupy every available stream slot without reading the responses yet.
            val occupied = (1..STREAM_LIMIT).map { index ->
                connection.openStream().also { it.sendHeaders(path = "/slow/$index", endStream = true) }
            }

            assertFailsWith<QuicException>("one stream past the advertised limit must be refused") {
                connection.openStream()
            }

            occupied.forEachIndexed { index, stream ->
                assertEquals("ok:${index + 1}", stream.awaitResponse(timeout = 60.seconds).bodyText)
            }
        }
    }

    /**
     * The limit is a concurrency limit, not a budget for the life of the connection: as streams
     * retire the server grants more credit, so a connection serves any number of sequential
     * requests through a limit of [STREAM_LIMIT].
     *
     * The credit arrives in a `MAX_STREAMS` frame, so it is not available the instant a response is
     * read — the first request after the slots free up may still be refused. That is why this waits
     * for credit rather than asserting on the very next attempt.
     */
    @Test
    fun `stream credit is returned as streams retire`() = runTest {
        createAndStartServer {
            get("/sequential/{index}") { call.respondText("ok:${call.parameters["index"]}") }
        }

        withHttp3Client(sslPort) { connection ->
            repeat(STREAM_LIMIT * 4) { attempt ->
                val index = attempt + 1
                val body = withStreamCredit(timeout = 30.seconds) {
                    connection.request(path = "/sequential/$index", timeout = 30.seconds).bodyText
                }
                assertEquals("ok:$index", body, "request $index past a limit of $STREAM_LIMIT must be served")
            }
        }
    }

    /** Retries [block] while the local QUIC stack is still out of stream credit. */
    private suspend fun <T> withStreamCredit(timeout: Duration, block: suspend () -> T): T {
        val deadline = System.nanoTime() + timeout.inWholeNanoseconds
        while (true) {
            try {
                return block()
            } catch (cause: QuicException) {
                if (System.nanoTime() >= deadline) throw cause
                delay(20)
            }
        }
    }

    private companion object {
        /** Matches `quicInitialMaxStreamsBidirectional` configured above. */
        private const val STREAM_LIMIT = 2
    }
}
