/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.engine.java

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.http.content.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*
import java.net.http.HttpRequest
import java.time.*
import java.util.*
import kotlin.coroutines.*

/**
 * Headers managed or restricted by `java.net.http.HttpClient` itself. Passing any of these to
 * `HttpRequest.Builder.header(...)` throws `IllegalArgumentException`, so the engine omits them:
 * [HttpHeaders.ContentLength] is derived from the [HttpRequest.BodyPublisher], and the JDK controls
 * the transport headers [HttpHeaders.Connection], [HttpHeaders.Expect] and [HttpHeaders.Upgrade].
 *
 * Other headers the JDK once restricted (`Date`, `From`, `Via`, `Warning`) are intentionally absent:
 * since [JDK-8213189](https://bugs.openjdk.org/browse/JDK-8213189) the JDK accepts them, so the
 * engine delegates to it rather than silently dropping caller-supplied values.
 */
internal val DISALLOWED_HEADERS = TreeSet(String.CASE_INSENSITIVE_ORDER).apply {
    addAll(
        setOf(
            HttpHeaders.Connection,
            HttpHeaders.ContentLength,
            HttpHeaders.Expect,
            HttpHeaders.Upgrade
        )
    )
}

@OptIn(InternalAPI::class)
internal fun HttpRequestData.convertToHttpRequest(callContext: CoroutineContext): HttpRequest {
    val builder = HttpRequest.newBuilder(url.toURI())

    with(builder) {
        getCapabilityOrNull(HttpTimeoutCapability)?.let { timeoutAttributes ->
            timeoutAttributes.requestTimeoutMillis?.let {
                if (!isTimeoutInfinite(it)) timeout(Duration.ofMillis(it))
            }
        }

        mergeHeaders(headers, body) { key, value ->
            if (!DISALLOWED_HEADERS.contains(key)) {
                header(key, value)
            }
        }

        if (method == HttpMethod.Get && body.isEmpty()) {
            GET()
        } else {
            method(method.value, body.convertToHttpRequestBody(callContext))
        }
    }

    return builder.build()
}

@OptIn(DelicateCoroutinesApi::class)
internal fun OutgoingContent.convertToHttpRequestBody(
    callContext: CoroutineContext
): HttpRequest.BodyPublisher = when (this) {
    is OutgoingContent.ByteArrayContent -> HttpRequest.BodyPublishers.ofByteArray(bytes())

    is OutgoingContent.ReadChannelContent -> JavaHttpRequestBodyPublisher(
        coroutineContext = callContext,
        contentLength = contentLength ?: -1
    ) { readFrom() }

    is OutgoingContent.WriteChannelContent -> JavaHttpRequestBodyPublisher(
        coroutineContext = callContext,
        contentLength = contentLength ?: -1
    ) { GlobalScope.writer(callContext) { writeTo(channel) }.channel }

    is OutgoingContent.NoContent -> HttpRequest.BodyPublishers.noBody()

    is OutgoingContent.ContentWrapper -> delegate().convertToHttpRequestBody(callContext)

    is OutgoingContent.ProtocolUpgrade -> throw UnsupportedContentTypeException(this)
}
