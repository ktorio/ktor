/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.client.engine.HttpClientEngineBase
import io.ktor.client.engine.HttpClientEngineCapability
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.HttpClientEngineFactory
import io.ktor.client.engine.callContext
import io.ktor.client.engine.mergeHeaders
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.Url
import io.ktor.http.content.OutgoingContent
import io.ktor.http.fullPath
import io.ktor.http.hostWithPort
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Test-only [io.ktor.client.engine.HttpClientEngine] speaking HTTP/3, so that the shared server
 * test suites can be run over HTTP/3 unchanged.
 *
 * Ktor has no HTTP/3 client engine, so this adapts [Http3TestClient] to the client engine contract.
 * It is a test harness, not a general-purpose engine: it keeps a single QUIC connection per
 * authority, does not pool, and rejects what HTTP/3 cannot express (protocol upgrades).
 */
class Http3TestClientEngine(
    override val config: HttpClientEngineConfig,
) : HttpClientEngineBase("ktor-http3-test") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability)

    private val connectionMutex = Mutex()
    private var connection: Http3TestConnection? = null
    private var connectionAuthority: String? = null

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()
        val requestTime = GMTDate()
        val responseTimeout = data.getCapabilityOrNull(HttpTimeoutCapability)
            ?.requestTimeoutMillis
            ?.milliseconds
            ?: Http3TestTimeouts.response

        val stream = withContext(Dispatchers.IO) {
            connection(data.url).openStream().also { stream ->
                stream.sendHeaders(
                    method = data.method.value,
                    path = data.url.fullPath,
                    headers = requestHeaders(data),
                )
            }
        }

        writeRequestBody(stream, data.body, callContext)

        val responseHeaders = withContext(Dispatchers.IO) { stream.awaitResponseHeaders(responseTimeout) }
        val status = responseHeaders.status?.toIntOrNull()
            ?: error("HTTP/3 response has no usable :status pseudo-header: ${responseHeaders.status}")

        val body = CoroutineScope(callContext).writer(Dispatchers.IO, autoFlush = true) {
            forwardResponseBody(stream, channel, responseTimeout)
        }.channel

        return HttpResponseData(
            statusCode = HttpStatusCode.fromValue(status),
            requestTime = requestTime,
            headers = responseHeaders.headers,
            version = HttpProtocolVersion.HTTP_3_0,
            body = body,
            callContext = callContext,
        )
    }

    override fun close() {
        connection?.close()
        connection = null
        connectionAuthority = null
        super.close()
    }

    /**
     * Returns the connection for [url], opening one if needed.
     *
     * A single connection is reused so that suite tests observe HTTP/3 stream multiplexing rather
     * than a handshake per request. Each test starts a server on a fresh port, so a change of
     * authority (or a connection the server has since closed) replaces it.
     */
    private suspend fun connection(url: Url): Http3TestConnection = connectionMutex.withLock {
        val authority = url.hostWithPort
        val current = connection

        if (current != null && connectionAuthority == authority && current.quicChannel.isActive) {
            return@withLock current
        }

        current?.close()
        openHttp3Connection(port = url.port, host = url.host, authority = authority).also {
            connection = it
            connectionAuthority = authority
        }
    }

    private suspend fun writeRequestBody(
        stream: Http3TestStream,
        content: OutgoingContent,
        callContext: CoroutineContext,
    ) {
        when (content) {
            is OutgoingContent.NoContent -> Unit

            is OutgoingContent.ByteArrayContent -> withContext(Dispatchers.IO) { stream.sendData(content.bytes()) }

            is OutgoingContent.ReadChannelContent -> forwardRequestBody(content.readFrom(), stream)

            is OutgoingContent.WriteChannelContent -> {
                val channel = CoroutineScope(callContext).writer(Dispatchers.IO, autoFlush = true) {
                    content.writeTo(channel)
                }.channel
                forwardRequestBody(channel, stream)
            }

            is OutgoingContent.ContentWrapper -> {
                writeRequestBody(stream, content.delegate(), callContext)
                return
            }

            is OutgoingContent.ProtocolUpgrade ->
                throw UnsupportedOperationException("HTTP/3 doesn't support upgrade")
        }

        withContext(Dispatchers.IO) { stream.endOutput() }
    }

    /** Streams [source] out as a sequence of DATA frames, one per chunk actually read. */
    private suspend fun forwardRequestBody(source: ByteReadChannel, stream: Http3TestStream) {
        val buffer = ByteArray(DEFAULT_CHUNK_SIZE)
        while (true) {
            val read = source.readAvailable(buffer)
            if (read <= 0) break
            withContext(Dispatchers.IO) { stream.sendData(buffer.copyOf(read)) }
        }
    }

    private suspend fun forwardResponseBody(
        stream: Http3TestStream,
        channel: ByteWriteChannel,
        timeout: Duration,
    ) {
        while (true) {
            when (val event = stream.nextEvent(timeout)) {
                null -> throw IOException("Timed out after $timeout waiting for the HTTP/3 response body")

                is Http3StreamEvent.Data -> channel.writeFully(event.bytes)

                // Trailers have no representation in HttpResponseData; Phase 2 asserts them directly.
                is Http3StreamEvent.Trailers -> Unit

                is Http3StreamEvent.ResponseHeaders -> Unit

                is Http3StreamEvent.Failure -> throw IOException(
                    "HTTP/3 stream failed with error code ${event.http3ErrorCode}",
                    event.cause
                )

                Http3StreamEvent.InputClosed -> break
            }
        }
    }

    private fun Http3TestStream.awaitResponseHeaders(timeout: Duration): Http3StreamEvent.ResponseHeaders {
        when (val event = nextEvent(timeout)) {
            null -> throw IOException("Timed out after $timeout waiting for the HTTP/3 response headers")

            is Http3StreamEvent.ResponseHeaders -> return event

            is Http3StreamEvent.Failure -> throw IOException(
                "HTTP/3 stream failed with error code ${event.http3ErrorCode} before response headers",
                event.cause
            )

            else -> throw IOException("Unexpected HTTP/3 event before response headers: $event")
        }
    }

    /**
     * Request headers minus everything HTTP/3 forbids: `host` is carried by `:authority`, and
     * connection-specific headers are prohibited by RFC 9114 § 4.2.
     */
    @OptIn(InternalAPI::class)
    private fun requestHeaders(data: HttpRequestData): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        mergeHeaders(data.headers, data.body) { key, value ->
            if (!key.equals(HttpHeaders.Host, ignoreCase = true) && key.lowercase() !in FORBIDDEN_HEADERS) {
                headers[key] = value
            }
        }
        return headers
    }

    private companion object {
        private const val DEFAULT_CHUNK_SIZE = 8 * 1024

        private val FORBIDDEN_HEADERS = setOf(
            "connection",
            "keep-alive",
            "proxy-connection",
            "transfer-encoding",
            "upgrade",
        )
    }
}

/** Factory for [Http3TestClientEngine], for use as `HttpClient(Http3TestEngine) { }`. */
object Http3TestEngine : HttpClientEngineFactory<HttpClientEngineConfig> {
    override fun create(block: HttpClientEngineConfig.() -> Unit): Http3TestClientEngine =
        Http3TestClientEngine(HttpClientEngineConfig().apply(block))
}
