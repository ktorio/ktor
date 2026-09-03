/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.server.netty.http3

import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.plugins.origin
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.test.base.EngineTestBase
import io.ktor.server.test.base.Http3TestConnection
import io.ktor.server.test.base.withHttp3Client
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.ExperimentalKtorApi
import io.ktor.utils.io.writeFully
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NettyHttp3ProtocolTest :
    EngineTestBase<NettyApplicationEngine, NettyApplicationEngine.Configuration>(Netty) {

    init {
        enableSsl = true
        enableHttp3 = true
    }

    @OptIn(ExperimentalKtorApi::class)
    override fun configure(configuration: NettyApplicationEngine.Configuration) {
        configuration.enableHttp3()
    }

    /**
     * Pins the client address the application actually observes over HTTP/3.
     *
     * `remoteHost`/`remoteAddress`/`remotePort` are unavailable, and `localPort` reports the scheme
     * default rather than the port the request arrived on:
     * `NettyHttp3ApplicationRequest.kt:78-79` casts the stream channel's addresses to
     * `InetSocketAddress`, but a `QuicStreamChannel` returns `QuicStreamAddress`, so both casts
     * yield `null` and `HttpMultiplexedConnectionPoint` falls back. HTTP/2 shares that code and
     * works only because its stream channel delegates to its parent — see the control test in
     * [NettyHttp2ConnectionPointControlTest].
     *
     * The fix is to read from `ctx.channel().parent()`. Until then this test documents the gap;
     * the shared-suite `testRemoteAddress` case is the failing evidence for it.
     */
    @Test
    fun `client address is unavailable over HTTP3`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            val origin = connection.originFields()

            assertEquals("unknown", origin["remoteHost"], "client host is not available over HTTP/3")
            assertEquals("unknown", origin["remoteAddress"], "client address is not available over HTTP/3")
            assertEquals("0", origin["remotePort"], "client port is not available over HTTP/3")

            assertEquals("localhost", origin["localHost"])
            assertEquals("localhost", origin["localAddress"])
            assertEquals("443", origin["localPort"], "falls back to the scheme default, not the real port")
        }
    }

    @Test
    fun `authority drives serverHost and serverPort`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            val origin = connection.originFields()

            assertEquals("localhost", origin["serverHost"])
            assertEquals(sslPort.toString(), origin["serverPort"], "taken from the :authority pseudo-header")
        }
    }

    /**
     * RFC 9114 § 4.3.1 allows a request to carry `Host` instead of `:authority`, and that is the
     * only way to reach the authority-less fallbacks in `HttpMultiplexedConnectionPoint` over a
     * real connection.
     *
     * Note what the fallback does *not* do: `Host` is ignored, so `serverHost`/`serverPort` report
     * the local connection point rather than the authority the client actually asked for.
     */
    @Test
    fun `with Host instead of authority the server falls back to the local connection point`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(
                path = "/connection-point",
                headers = mapOf(HttpHeaders.Host to "localhost:$sslPort"),
                authority = null,
                endStream = true,
            )
            val origin = parseFields(stream.awaitResponse().bodyText)

            assertEquals("localhost", origin["serverHost"], "falls back to localHost, ignoring Host")
            assertEquals("443", origin["serverPort"], "falls back to localPort, itself the scheme default")
        }
    }

    /**
     * A request with neither `:authority` nor `Host` is malformed under RFC 9114 § 4.3.1, and is
     * correctly rejected: `Http3HeadersSink.finish()` fails its `authorityOrHostHeaderReceived()`
     * check and `Http3FrameCodec` resets the stream with `H3_MESSAGE_ERROR`, so the request never
     * reaches the application.
     *
     * From the client all that is visible is a stream that closed without an HTTP response — this
     * harness surfaces a clean input close rather than the reset, so the error code the server sent
     * cannot be observed here. See `NettyHttp3ErrorTest` for the full set of malformed shapes.
     */
    @Test
    fun `a request with no authority and no Host gets no HTTP response`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(path = "/connection-point", authority = null, endStream = true)
            val response = stream.awaitResponse()

            assertEquals("", response.status, "no HTTP response status is sent")
            assertEquals("", response.bodyText)
        }
    }

    @Test
    fun `request reports HTTP3 as its version`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            // Note: "HTTP/3", not "HTTP/3.0" as HttpProtocolVersion.HTTP_3_0 renders it.
            assertEquals("HTTP/3", connection.originFields()["version"])
        }
    }

    @Test
    fun `scheme uri and query survive the wire`() = runTest {
        createAndStartServer {
            get("/connection-point") { call.respondText(call.describeOrigin()) }
        }

        withHttp3Client(sslPort) { connection ->
            val stream = connection.openStream()
            stream.sendHeaders(path = "/connection-point?a=1&b=two%20words", endStream = true)
            val origin = parseFields(stream.awaitResponse().bodyText)

            assertEquals("https", origin["scheme"])
            assertEquals("/connection-point?a=1&b=two%20words", origin["uri"])
            assertEquals("GET", origin["method"])
        }
    }

    @Test
    fun `responses carry no transfer encoding header`() = runTest {
        createAndStartServer {
            get("/buffered") { call.respondText("body") }
            get("/streaming") {
                call.respondBytesWriter { writeFully("streamed".toByteArray()) }
            }
        }

        withHttp3Client(sslPort) { connection ->
            // HTTP/3 frames bodies in DATA frames; the header is stripped in
            // NettyHttp3ApplicationResponse.responseMessage.
            assertNull(connection.request(path = "/buffered").headers[HttpHeaders.TransferEncoding])
            assertNull(connection.request(path = "/streaming").headers[HttpHeaders.TransferEncoding])
        }
    }

    @Test
    fun `a multi megabyte response body is byte exact`() = runTest {
        // Guards the shutdownOutput()/context.close() ordering that PR #5822 changed: a premature
        // output close truncates the tail of a large body.
        val payload = ByteArray(4 * 1024 * 1024) { (it % 251).toByte() }

        createAndStartServer {
            get("/large") { call.respondBytes(payload) }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/large")
            assertEquals("200", response.status)
            assertEquals(payload.size, response.body.size, "response body was truncated")
            assertContentEquals(payload, response.body)
        }
    }

    @Test
    fun `a streaming response has no content length and is complete`() = runTest {
        val chunk = "0123456789".repeat(1000).toByteArray()
        val chunks = 20

        createAndStartServer {
            get("/no-length") {
                call.respondBytesWriter {
                    repeat(chunks) { writeFully(chunk) }
                }
            }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/no-length")
            assertEquals("200", response.status)
            assertNull(response.headers[HttpHeaders.ContentLength], "a streamed body has no known length")
            assertEquals(chunk.size * chunks, response.body.size)
        }
    }

    /**
     * Response trailers are dropped for a buffered body.
     *
     * `NettyHttp3ApplicationResponse.respondOutgoingContent` calls `super.respondOutgoingContent`
     * *before* copying `content.trailers()` into the response trailers. For a buffered body the
     * super call writes the whole response synchronously, so `prepareTrailerMessage()` is consulted
     * (`NettyHttpResponsePipeline.kt:314`) while the trailers are still empty and returns `null`.
     *
     * The streaming case below passes only because its body is written asynchronously, so the
     * append happens to win the race. Appending the trailers before delegating to `super` fixes
     * both.
     */
    @Test
    @Ignore("HTTP/3 response trailers are appended after the body is written; see the KDoc above")
    fun `response trailers arrive for a buffered body`() = runTest {
        createAndStartServer {
            get("/trailers") { call.respond(BufferedContentWithTrailers) }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/trailers")
            assertEquals("buffered", response.bodyText)
            assertEquals(TRAILER_VALUE, response.trailers[TRAILER_NAME], "trailing HEADERS frame is missing")
        }
    }

    /**
     * The streaming case is not a working path, it is the same defect showing up as flakiness: the
     * body is written asynchronously, so whether the trailers are appended before
     * `prepareTrailerMessage()` is consulted differs from run to run. Observed passing and failing
     * across consecutive runs of this test.
     */
    @Test
    @Ignore("HTTP/3 response trailers race the body write on streaming responses; see the KDoc above")
    fun `response trailers arrive for a streaming body`() = runTest {
        createAndStartServer {
            get("/trailers") { call.respond(StreamingContentWithTrailers) }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/trailers")
            assertEquals("streamed", response.bodyText)
            assertEquals(TRAILER_VALUE, response.trailers[TRAILER_NAME], "trailing HEADERS frame is missing")
        }
    }

    /**
     * Response trailers are dropped for an empty body, for a second, independent reason:
     * `respondWithEmptyBody` (`NettyHttpResponsePipeline.kt:291-293`) emits only
     * `prepareEndOfStreamMessage(false)` and never consults `prepareTrailerMessage()` — despite its
     * own KDoc claiming it "writes trailer message ... when response body is empty".
     */
    @Test
    @Ignore("respondWithEmptyBody never emits a trailer message; see the KDoc above")
    fun `response trailers arrive for an empty body`() = runTest {
        createAndStartServer {
            get("/trailers") { call.respond(EmptyContentWithTrailers) }
        }

        withHttp3Client(sslPort) { connection ->
            val response = connection.request(path = "/trailers")
            assertEquals("", response.bodyText)
            assertEquals(TRAILER_VALUE, response.trailers[TRAILER_NAME], "trailing HEADERS frame is missing")
        }
    }

    private companion object {
        private const val TRAILER_NAME = "x-checksum"
        private const val TRAILER_VALUE = "abc123"

        private val trailerHeaders = headersOf(TRAILER_NAME, TRAILER_VALUE)

        private val BufferedContentWithTrailers = object : OutgoingContent.ByteArrayContent() {
            override fun bytes(): ByteArray = "buffered".toByteArray()
            override fun trailers(): Headers = trailerHeaders
        }

        private val StreamingContentWithTrailers = object : OutgoingContent.WriteChannelContent() {
            override suspend fun writeTo(channel: ByteWriteChannel) {
                channel.writeFully("streamed".toByteArray())
            }

            override fun trailers(): Headers = trailerHeaders
        }

        private val EmptyContentWithTrailers = object : OutgoingContent.NoContent() {
            override fun trailers(): Headers = trailerHeaders
        }
    }
}

/**
 * Reports the fields of `call.request.origin` as `name=value` lines, so a test can assert on what
 * the application actually observes.
 */
internal fun io.ktor.server.application.ApplicationCall.describeOrigin(): String {
    val origin = request.origin
    return listOf(
        "remoteHost" to origin.remoteHost,
        "remoteAddress" to origin.remoteAddress,
        "remotePort" to origin.remotePort.toString(),
        "localHost" to origin.localHost,
        "localAddress" to origin.localAddress,
        "localPort" to origin.localPort.toString(),
        "serverHost" to origin.serverHost,
        "serverPort" to origin.serverPort.toString(),
        "scheme" to origin.scheme,
        "version" to origin.version,
        "uri" to origin.uri,
        "method" to origin.method.value,
    ).joinToString("\n") { (name, value) -> "$name=$value" }
}

internal fun parseFields(body: String): Map<String, String> =
    body.lineSequence()
        .filter { it.isNotBlank() }
        .associate { line -> line.substringBefore('=') to line.substringAfter('=') }

private fun Http3TestConnection.originFields(): Map<String, String> =
    parseFields(request(path = "/connection-point").bodyText)
