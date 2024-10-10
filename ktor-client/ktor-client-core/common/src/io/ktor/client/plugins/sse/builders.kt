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
import io.ktor.util.reflect.*
import kotlinx.coroutines.*
import kotlin.time.*

internal val sseRequestAttr = AttributeKey<Boolean>("SSERequestFlag")
internal val reconnectionTimeAttr = AttributeKey<Duration>("SSEReconnectionTime")
internal val showCommentEventsAttr = AttributeKey<Boolean>("SSEShowCommentEvents")
internal val showRetryEventsAttr = AttributeKey<Boolean>("SSEShowRetryEvents")
internal val deserializerAttr = AttributeKey<(TypeInfo, String) -> Any>("SSEDeserializer")

/**
 * Installs the [SSE] plugin using the [config] as configuration.
 */
public fun HttpClientConfig<*>.SSE(config: SSEConfig.() -> Unit) {
    install(SSE) {
        config()
    }
}

// Builders for the `ClientSSESession`

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = processSession(reconnectionTime, showCommentEvents, showRetryEvents, block) {}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents) {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents) {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
) {
    val session = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents, request)
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
public suspend fun HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url(scheme, host, port, path)
            request()
        },
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url.takeFrom(urlString)
            request()
        },
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession =
    serverSentEventsSession(scheme, host, port, path, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(urlString, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(request, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit =
    serverSentEvents(scheme, host, port, path, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(urlString, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

// Builders for the `ClientSSESessionWithDeserialization`

/**
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEventsSession(
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESessionWithDeserialization = processSession(reconnectionTime, showCommentEvents, showRetryEvents, block) {
    addAttribute(
        deserializerAttr,
        deserialize
    )
}

/**
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents) {
        url(scheme, host, port, path)
        block()
    }

/**
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents) {
        url.takeFrom(urlString)
        block()
    }

/**
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
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
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
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
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
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
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sseSession(
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESessionWithDeserialization = serverSentEventsSession(
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
 * Opens a [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(urlString, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
): Unit = serverSentEvents(request, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
): Unit = serverSentEvents(
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
 * Opens a [block] with [ClientSSESessionWithDeserialization].
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: (TypeInfo, String) -> Any,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
): Unit = serverSentEvents(urlString, deserialize, reconnectionTime, showCommentEvents, showRetryEvents, request, block)

private suspend inline fun <reified T> HttpClient.processSession(
    reconnectionTime: Duration?,
    showCommentEvents: Boolean?,
    showRetryEvents: Boolean?,
    block: HttpRequestBuilder.() -> Unit,
    additionalAttributes: HttpRequestBuilder.() -> Unit
): T {
    plugin(SSE)

    val sessionDeferred = CompletableDeferred<T>()
    val statement = prepareRequest {
        block()
        addAttribute(sseRequestAttr, true)
        addAttribute(reconnectionTimeAttr, reconnectionTime)
        addAttribute(showCommentEventsAttr, showCommentEvents)
        addAttribute(showRetryEventsAttr, showRetryEvents)
        additionalAttributes()
    }
    @Suppress("SuspendFunctionOnCoroutineScope")
    launch {
        try {
            statement.body<T, Unit> { session ->
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
