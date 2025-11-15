/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.sse.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.CoroutineContext

/**
 * An Android client engine.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.engine.android.AndroidClientEngine)
 */
@OptIn(InternalAPI::class)
public class AndroidClientEngine(override val config: AndroidEngineConfig) : HttpClientEngineBase("ktor-android") {

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeoutCapability, SSECapability)

    private val urlFactory = if (config.httpEngineDisabled ||
        !isHttpEngineAvailable() ||
        config.proxy != null ||
        config.context == null
    ) {
        URLConnectionFactory.StandardURLConnectionFactory(config)
    } else {
        AndroidNetHttpEngineFactory(config)
    }

    /**
     * Executes the given HTTP request and returns the resulting response.
     *
     * The request described by `data` is sent over a configured HttpURLConnection; the request body
     * (if any) is written to the connection, and the response status, headers, protocol version, and
     * body (or an adapted representation when a ResponseAdapterAttribute is present) are returned.
     *
     * @param data The HTTP request data to execute (URL, method, headers, body, and attributes).
     * @return The HttpResponseData containing the response status, headers, protocol version, body, and call context.
     * @throws IllegalStateException If the request method does not allow a body but a non-empty body is provided.
     */
    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val requestTime = GMTDate()

        val url: String = data.url.toString()
        val outgoingContent: OutgoingContent = data.body
        val contentLength: Long? = data.headers[HttpHeaders.ContentLength]?.toLong()
            ?: outgoingContent.contentLength

        val connection: HttpURLConnection = urlFactory(url).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout

            setupTimeoutAttributes(data)

            // TODO document not active on Android 14
            if (this is HttpsURLConnection) {
                config.sslManager(this)
            }

            requestMethod = data.method.value
            useCaches = false
            instanceFollowRedirects = false

            data.forEachHeader(::addRequestProperty)

            config.requestConfig(this)

            if (!data.method.supportsRequestBody) {
                if (outgoingContent.isEmpty()) {
                    return@apply
                }

                error("Request of type ${data.method} couldn't send a body with the [Android] engine.")
            }

            if (contentLength == null && getRequestProperty(HttpHeaders.TransferEncoding) == null) {
                addRequestProperty(HttpHeaders.TransferEncoding, "chunked")
            }

            contentLength?.let { setFixedLengthStreamingMode(it) } ?: setChunkedStreamingMode(0)
            doOutput = true

            outgoingContent.writeTo(outputStream, callContext)
        }

        return connection.timeoutAwareConnection(data) { current ->
            val responseCode = current.responseCode
            val responseMessage = current.responseMessage
            val statusCode = responseMessage?.let { HttpStatusCode(responseCode, it) }
                ?: HttpStatusCode.fromValue(responseCode)

            val content: ByteReadChannel = current.content(responseCode, callContext)
            val headerFields: Map<String, List<String>> = current.headerFields
                .mapKeys { it.key?.lowercase(Locale.getDefault()) ?: "" }
                .filter { it.key.isNotBlank() }

            val version: HttpProtocolVersion = urlFactory.protocolFromRequest(connection)
            val responseHeaders = HeadersImpl(headerFields)

            val responseBody: Any = data.attributes.getOrNull(ResponseAdapterAttributeKey)
                ?.adapt(data, statusCode, responseHeaders, content, outgoingContent, callContext)
                ?: content

            HttpResponseData(statusCode, requestTime, responseHeaders, version, responseBody, callContext)
        }
    }
}

/**
 * Writes this [OutgoingContent] into the provided [stream], honoring the supplied [callContext].
 *
 * Supports ByteArrayContent, ReadChannelContent, WriteChannelContent, NoContent and ContentWrapper;
 * for WriteChannelContent a writer is launched with [callContext] and its resulting channel is copied to the stream.
 * The stream is closed after writing completes.
 *
 * @param stream The destination [OutputStream] to write the content into; it will be closed when writing finishes.
 * @param callContext The coroutine context used when producing content for `WriteChannelContent`.
 *
 * @throws UnsupportedContentTypeException if this content is a [OutgoingContent.ProtocolUpgrade].
 */
@OptIn(DelicateCoroutinesApi::class)
internal suspend fun OutgoingContent.writeTo(
    stream: OutputStream,
    callContext: CoroutineContext
): Unit = stream.use { blockingOutput ->
    when (this) {
        is OutgoingContent.ByteArrayContent -> blockingOutput.write(bytes())
        is OutgoingContent.ReadChannelContent -> run {
            readFrom().copyTo(blockingOutput)
        }

        is OutgoingContent.WriteChannelContent -> {
            val channel = GlobalScope.writer(callContext) {
                writeTo(channel)
            }.channel

            channel.copyTo(blockingOutput)
        }

        is OutgoingContent.NoContent -> {
        }

        is OutgoingContent.ContentWrapper -> delegate().writeTo(stream, callContext)

        is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
    }
}
