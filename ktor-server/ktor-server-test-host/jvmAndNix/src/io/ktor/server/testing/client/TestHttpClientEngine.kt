/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing.client

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.testing.*
import io.ktor.server.testing.internal.*
import io.ktor.util.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal expect class TestHttpClientEngineBridge(engine: TestHttpClientEngine, app: TestApplicationEngine) {
    val supportedCapabilities: Set<HttpClientEngineCapability<*>>

    suspend fun runWebSocketRequest(
        url: String,
        headers: Headers,
        content: OutgoingContent,
        coroutineContext: CoroutineContext
    ): Pair<TestApplicationCall, WebSocketSession>
}

public class TestHttpClientEngine(override val config: TestHttpClientConfig) : HttpClientEngineBase("ktor-test") {
    private val app: TestApplicationEngine = config.app

    private val bridge = TestHttpClientEngineBridge(this, app)

    override val supportedCapabilities = bridge.supportedCapabilities

    override val dispatcher = Dispatchers.IOBridge

    private val clientJob: CompletableJob = Job(app.coroutineContext[Job])

    override val coroutineContext: CoroutineContext = dispatcher + clientJob

    @OptIn(InternalAPI::class)
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        app.start()
        if (data.isUpgradeRequest()) {
            val (testServerCall, session) = with(data) {
                bridge.runWebSocketRequest(url.fullPath, headers, body, callContext())
            }
            return with(testServerCall.response) {
                httpResponseData(session)
            }
        }

        val testServerCall = with(data) {
            runRequest(method, url.fullPath, headers, body, url.protocol)
        }

        return with(testServerCall.response) {
            httpResponseData(ByteReadChannel(byteContent ?: byteArrayOf()))
        }
    }

    private fun runRequest(
        method: HttpMethod,
        url: String,
        headers: Headers,
        content: OutgoingContent,
        protocol: URLProtocol
    ): TestApplicationCall {
        return app.handleRequest(method, url) {
            appendRequestHeaders(headers, content)
            this.protocol = protocol.name

            if (content !is OutgoingContent.NoContent) {
                bodyChannel = content.toByteReadChannel()
            }
        }
    }

    @OptIn(InternalAPI::class)
    private suspend fun TestApplicationResponse.httpResponseData(body: Any) = HttpResponseData(
        status() ?: HttpStatusCode.NotFound,
        GMTDate(),
        headers.allValues().takeUnless { it.isEmpty() } ?: Headers
            .build { append(HttpHeaders.ContentLength, "0") },
        HttpProtocolVersion.HTTP_1_1,
        body,
        callContext()
    )

    internal fun TestApplicationRequest.appendRequestHeaders(
        headers: Headers,
        content: OutgoingContent
    ) {
        headers.flattenForEach { name, value ->
            if (HttpHeaders.ContentLength == name) return@flattenForEach // set later
            if (HttpHeaders.ContentType == name) return@flattenForEach // set later
            addHeader(name, value)
        }

        content.headers.flattenForEach { name, value ->
            if (HttpHeaders.ContentLength == name) return@flattenForEach // TODO: throw exception for unsafe header?
            if (HttpHeaders.ContentType == name) return@flattenForEach
            addHeader(name, value)
        }

        val contentLength = headers[HttpHeaders.ContentLength] ?: content.contentLength?.toString()
        val contentType = headers[HttpHeaders.ContentType] ?: content.contentType?.toString()

        contentLength?.let { addHeader(HttpHeaders.ContentLength, it) }
        contentType?.let { addHeader(HttpHeaders.ContentType, it) }
    }

    override fun close() {
        clientJob.complete()
    }

    companion object : HttpClientEngineFactory<TestHttpClientConfig> {
        override fun create(block: TestHttpClientConfig.() -> Unit): HttpClientEngine {
            val config = TestHttpClientConfig().apply(block)
            return TestHttpClientEngine(config)
        }
    }

    private fun OutgoingContent.toByteReadChannel(): ByteReadChannel = when (this) {
        is OutgoingContent.NoContent -> ByteReadChannel.Empty
        is OutgoingContent.ByteArrayContent -> ByteReadChannel(bytes())
        is OutgoingContent.ReadChannelContent -> readFrom()
        is OutgoingContent.WriteChannelContent -> runBlocking {
            writer(coroutineContext) { writeTo(channel) }.channel
        }
        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}
