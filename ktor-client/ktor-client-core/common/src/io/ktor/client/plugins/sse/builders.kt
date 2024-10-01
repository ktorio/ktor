/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlin.time.*

internal val sseRequestAttr = AttributeKey<Boolean>("SSERequestFlag")
internal val reconnectionTimeAttr = AttributeKey<Duration>("SSEReconnectionTime")
internal val showCommentEventsAttr = AttributeKey<Boolean>("SSEShowCommentEvents")
internal val showRetryEventsAttr = AttributeKey<Boolean>("SSEShowRetryEvents")
internal val deserializerAttr = AttributeKey<(String) -> Any>("SSEDeserializer")

/**
 * Installs the [SSE] plugin using the [config] as configuration.
 */
public fun HttpClientConfig<*>.SSE(config: SSEConfig.() -> Unit) {
    install(SSE) {
        config()
    }
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEventsSession(
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession<T> {
    plugin(SSE)

    val sessionDeferred = CompletableDeferred<ClientSSESession<T>>()
    val statement = prepareRequest {
        block()
        addAttribute(sseRequestAttr, true)
        addAttribute(deserializerAttr, deserialize)
        addAttribute(reconnectionTimeAttr, reconnectionTime)
        addAttribute(showCommentEventsAttr, showCommentEvents)
        addAttribute(showRetryEventsAttr, showRetryEvents)
    }
    @Suppress("SuspendFunctionOnCoroutineScope")
    launch {
        try {
            statement.body<ClientSSESession<T>, Unit> { session ->
                sessionDeferred.complete(session)
            }
        } catch (cause: CancellationException) {
            sessionDeferred.cancel(cause)
        } catch (cause: Throwable) {
            sessionDeferred.completeExceptionally(mapToSSEException(response = null, cause))
        }
    }
    return sessionDeferred.await()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: ((String) -> T)?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession<T> = serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents) {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEventsSession(
    urlString: String,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession<T> = serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents) {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession<T>.() -> Unit
) {
    val session = serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents, request)
    try {
        block(session)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        throw mapToSSEException(session.call.response, cause)
    } finally {
        session.cancel()
    }
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession<T>.() -> Unit
) {
    serverSentEvents(
        {
            url(scheme, host, port, path)
            request()
        },
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.serverSentEvents(
    urlString: String,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession<T>.() -> Unit
) {
    serverSentEvents(
        {
            url.takeFrom(urlString)
            request()
        },
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sseSession(
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession<T> =
    serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession<T> =
    serverSentEventsSession(
        scheme,
        host,
        port,
        path,
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )

/**
 * Opens a [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sseSession(
    urlString: String,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession<T> =
    serverSentEventsSession(urlString, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession<T>.() -> Unit
): Unit = serverSentEvents(request, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession<T>.() -> Unit
): Unit =
    serverSentEvents(
        scheme,
        host,
        port,
        path,
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        request,
        block
    )

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun <T : Any> HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: ((String) -> T)? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession<T>.() -> Unit
): Unit = serverSentEvents(urlString, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

private fun <T : Any> HttpRequestBuilder.addAttribute(attributeKey: AttributeKey<T>, value: T?) {
    if (value != null) {
        attributes.put(attributeKey, value)
    }
}

private fun mapToSSEException(response: HttpResponse?, cause: Throwable): Throwable {
    return if (cause is SSEClientException && cause.response != null) {
        cause
    } else {
        SSEClientException(response, cause, cause.message)
    }
}
