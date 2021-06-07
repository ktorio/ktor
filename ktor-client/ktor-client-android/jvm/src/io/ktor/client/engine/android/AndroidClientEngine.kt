/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.android

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.util.date.*
import io.ktor.utils.io.*
import io.ktor.utils.io.jvm.javaio.*
import kotlinx.coroutines.*
import java.io.*
import java.net.*
import java.util.*
import javax.net.ssl.*
import kotlin.coroutines.*

private val METHODS_WITHOUT_BODY = listOf(HttpMethod.Get, HttpMethod.Head)

/**
 * Android client engine
 */
public class AndroidClientEngine(override val config: AndroidEngineConfig) : HttpClientEngineBase("ktor-android") {

    override val dispatcher: CoroutineDispatcher by lazy {
        Dispatchers.clientDispatcher(
            config.threadsCount,
            "ktor-android-dispatcher"
        )
    }

    override val supportedCapabilities: Set<HttpClientEngineCapability<*>> = setOf(HttpTimeout)

    override suspend fun execute(data: HttpRequestData): HttpResponseData {
        val callContext = callContext()

        val requestTime = GMTDate()

        val url: String = URLBuilder().takeFrom(data.url).buildString()
        val outgoingContent: OutgoingContent = data.body
        val contentLength: Long? = data.headers[HttpHeaders.ContentLength]?.toLong()
            ?: outgoingContent.contentLength

        val connection: HttpURLConnection = getProxyAwareConnection(url).apply {
            connectTimeout = config.connectTimeout
            readTimeout = config.socketTimeout

            setupTimeoutAttributes(data)

            if (this is HttpsURLConnection) {
                config.sslManager(this)
            }

            requestMethod = data.method.value
            useCaches = false
            instanceFollowRedirects = false

            mergeHeaders(data.headers, outgoingContent) { key: String, value: String ->
                addRequestProperty(key, value)
            }

            config.requestConfig(this)

            if (data.method in METHODS_WITHOUT_BODY) {
                if (outgoingContent is OutgoingContent.NoContent) {
                    return@apply
                }

                error("Request of type ${data.method} couldn't send a body with the [Android] engine.")
            }

            if (contentLength == null && getRequestProperty(HttpHeaders.TransferEncoding) == null) {
                addRequestProperty(HttpHeaders.TransferEncoding, "chunked")
            }

            contentLength?.let { setFixedLengthStreamingMode(it.toInt()) } ?: setChunkedStreamingMode(0)
            doOutput = true

            outgoingContent.writeTo(outputStream, callContext)
        }

        return connection.timeoutAwareConnection(data) { connection ->
            val responseCode = connection.responseCode
            val responseMessage = connection.responseMessage
            val statusCode = responseMessage?.let { HttpStatusCode(responseCode, it) }
                ?: HttpStatusCode.fromValue(responseCode)

            val content: ByteReadChannel = connection.content(callContext, data)
            val headerFields: Map<String, List<String>> = connection.headerFields
                .mapKeys { it.key?.lowercase(Locale.getDefault()) ?: "" }
                .filter { it.key.isNotBlank() }

            val version: HttpProtocolVersion = HttpProtocolVersion.HTTP_1_1
            val responseHeaders = HeadersImpl(headerFields)

            HttpResponseData(statusCode, requestTime, responseHeaders, version, content, callContext)
        }
    }

    private fun getProxyAwareConnection(urlString: String): HttpURLConnection {
        val url = URL(urlString)
        val connection: URLConnection = config.proxy?.let { url.openConnection(it) } ?: url.openConnection()
        return connection as HttpURLConnection
    }
}

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
        else -> throw UnsupportedContentTypeException(this)
    }
}
