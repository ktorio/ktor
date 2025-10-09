/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.call.HttpClientCall.Companion.CustomResponse
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.reflect.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlin.time.Duration

internal val sseRequestAttr = AttributeKey<Boolean>("SSERequestFlag")
internal val reconnectionTimeAttr = AttributeKey<Duration>("SSEReconnectionTime")
internal val showCommentEventsAttr = AttributeKey<Boolean>("SSEShowCommentEvents")
internal val showRetryEventsAttr = AttributeKey<Boolean>("SSEShowRetryEvents")
internal val deserializerAttr = AttributeKey<(TypeInfo, String) -> Any?>("SSEDeserializer")
internal val sseBufferPolicyAttr = AttributeKey<SSEBufferPolicy>("bufferPolicy")

/**
 * Installs the [SSE] plugin using the [config] as configuration.
 *
 * Example of usage:
 * ```kotlin
 * val client = HttpClient() {
 *     SSE {
 *         showCommentEvents()
 *         showRetryEvents()
 *         bufferPolicy = SSEBufferPolicy.LastEvent
 *     }
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSE)
 */
@Suppress("FunctionName")
public fun HttpClientConfig<*>.SSE(config: SSEConfig.() -> Unit) {
    install(SSE) {
        config()
    }
}

// Builders for the `ClientSSESession`

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
 */
public suspend fun HttpClient.serverSentEventsSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = processSession(reconnectionTime, showCommentEvents, showRetryEvents, block) {}

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
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
): ClientSSESession = serverSentEventsSession(
    reconnectionTime,
    showCommentEvents,
    showRetryEvents
) {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(
    reconnectionTime,
    showCommentEvents,
    showRetryEvents,
) {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.serverSentEvents("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
 */
@OptIn(InternalAPI::class)
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
) {
    val session =
        serverSentEventsSession(reconnectionTime, showCommentEvents, showRetryEvents, request)
    try {
        block(session)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        throw mapToSSEException(session.call, session.bodyBuffer(), cause)
    } finally {
        session.cancel()
    }
}

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.serverSentEvents("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
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
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.serverSentEvents("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
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
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
 */
public suspend fun HttpClient.sseSession(
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = serverSentEventsSession(
    reconnectionTime,
    showCommentEvents,
    showRetryEvents,
    block
)

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
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
    serverSentEventsSession(
        scheme,
        host,
        port,
        path,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession {
 *     url("http://localhost:8080/sse")
 * }
 * session.incoming.collect { event ->
 *     println("Id: ${event.id}")
 *     println("Event: ${event.event}")
 *     println("Data: ${event.data}")
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession =
    serverSentEventsSession(urlString, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(request, reconnectionTime, showCommentEvents, showRetryEvents, block)

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
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
    serverSentEvents(
        scheme,
        host,
        port,
        path,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        request,
        block
    )

/**
 * Opens a [ClientSSESession] to receive Server-Sent Events (SSE) from a server and performs [block].
 *
 * Note: [ClientSSESession] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse("http://localhost:8080/sse") { // `this` is `ClientSSESession`
 *     incoming.collect { event ->
 *         println("Id: ${event.id}")
 *         println("Event: ${event.event}")
 *         println("Data: ${event.data}")
 *     }
 * }
 * ```
 *
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(
    urlString,
    reconnectionTime,
    showCommentEvents,
    showRetryEvents,
    request,
    block
)

// Builders for the `ClientSSESessionWithDeserialization`

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent`.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
 */
public suspend fun HttpClient.serverSentEventsSession(
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESessionWithDeserialization =
    processSession(reconnectionTime, showCommentEvents, showRetryEvents, block) {
        addAttribute(
            deserializerAttr,
            deserialize
        )
    }

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent`.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent`.
 *
 * Example of usage:
 * ```kotlin
 * val session = client.serverSentEventsSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEventsSession)
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent`.
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.serverSentEvents({
 *     url("http://localhost:8080/sse")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
 */
@OptIn(InternalAPI::class)
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
) {
    val session =
        serverSentEventsSession(
            deserialize,
            reconnectionTime,
            showCommentEvents,
            showRetryEvents,
            request
        )
    try {
        block(session)
    } catch (cause: CancellationException) {
        throw cause
    } catch (cause: Throwable) {
        throw mapToSSEException(session.call, session.bodyBuffer(), cause)
    } finally {
        session.cancel()
    }
}

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.serverSentEvents({
 *     url("http://localhost:8080/sse")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
 */
public suspend fun HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `serverSentEvents { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/serverSentEvents")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.serverSentEvents)
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
 */
public suspend fun HttpClient.sseSession(
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Example of usage:
 * ```kotlin
 * val session = client.sseSession("http://localhost:8080/sse", deserialize = { typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * })
 *
 * session.apply {
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sseSession)
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESessionWithDeserialization =
    serverSentEventsSession(
        urlString,
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/sse")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
): Unit =
    serverSentEvents(
        request,
        deserialize,
        reconnectionTime,
        showCommentEvents,
        showRetryEvents,
        block
    )

/**
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/sse")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: (TypeInfo, String) -> Any?,
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
 * Opens a [ClientSSESessionWithDeserialization] to receive Server-Sent Events (SSE) from a server with ability to
 * deserialize the `data` field of the `TypedServerSentEvent` and performs [block].
 *
 * Note: [ClientSSESessionWithDeserialization] is bound to the session lifetime.
 * Its scope is canceled when the `sse { ... }` block returns or when the connection closes.
 *
 * Example of usage:
 * ```kotlin
 * client.sse({
 *     url("http://localhost:8080/sse")
 * }, deserialize = {
 *     typeInfo, jsonString ->
 *     val serializer = Json.serializersModule.serializer(typeInfo.kotlinType!!)
 *     Json.decodeFromString(serializer, jsonString)!!
 * }) { // `this` is `ClientSSESessionWithDeserialization`
 *     incoming.collect { event: TypedServerSentEvent<String> ->
 *         when (event.event) {
 *             "customer" -> {
 *                 val customer: Customer? = deserialize<Customer>(event.data)
 *             }
 *             "product" -> {
 *                 val product: Product? = deserialize<Product>(event.data)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * @param deserialize The deserializer function to transform the `data` field of the `TypedServerSentEvent`
 *                    into an object
 * @param reconnectionTime The time duration to wait before attempting reconnection in case of connection loss
 * @param showCommentEvents When enabled, events containing only comments field will be presented in the incoming flow
 * @param showRetryEvents When enabled, retry directives (lines starting with `retry:`) are emitted as events
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.sse)
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    deserialize: (TypeInfo, String) -> Any?,
    reconnectionTime: Duration? = null,
    showCommentEvents: Boolean? = null,
    showRetryEvents: Boolean? = null,
    block: suspend ClientSSESessionWithDeserialization.() -> Unit
): Unit = serverSentEvents(
    urlString,
    deserialize,
    reconnectionTime,
    showCommentEvents,
    showRetryEvents,
    request,
    block
)

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
            sessionDeferred.completeExceptionally(mapToSSEException(call = null, body = null, cause))
        }
    }
    return sessionDeferred.await()
}

private fun <T : Any> HttpRequestBuilder.addAttribute(attributeKey: AttributeKey<T>, value: T?) {
    if (value != null) {
        attributes.put(attributeKey, value)
    }
}

private fun HttpClient.mapToSSEException(call: HttpClientCall?, body: ByteArray?, cause: Throwable): Throwable {
    val response = if (call == null) {
        null
    } else {
        val body = body ?: ByteArray(0)
        val savedCall = SavedHttpCall(this, call.request, call.response, body)
        savedCall.attributes.remove(CustomResponse)
        savedCall.attributes.remove(sseRequestAttr)
        val response = SavedHttpResponse(savedCall, body, call.response)
        savedCall.setResponse(response)
        response
    }

    return if (cause is SSEClientException && cause.response != null) {
        cause
    } else {
        SSEClientException(response, cause, cause.message)
    }
}

/**
 * Controls how the plugin captures a diagnostic buffer of the SSE stream that has already been
 * processed, so you can inspect it when an exception occurs.
 *
 * The buffer is built from bytes the SSE reader has already read, it does not re-read the network.
 *
 * Variants:
 * - [SSEBufferPolicy.Off] — capture is disabled (default).
 * - [SSEBufferPolicy.LastLines] — keeps the last N text lines of the stream.
 * - [SSEBufferPolicy.LastEvent] — keeps the last completed SSE event.
 * - [SSEBufferPolicy.LastEvents] — keeps the last K completed SSE events.
 * - [SSEBufferPolicy.All] — keeps everything that has been read so far. Please note that this may consume a lot of memory.
 *
 * Notes:
 * - This policy applies to failures after the SSE stream has started (e.g., parsing errors or exceptions
 *   thrown inside your `client.sse { ... }` block). It does not affect "handshake" failures
 *   (non-2xx status or non-`text/event-stream`); those are handled separately.
 * - The buffer reflects only what has already been consumed by the SSE parser at the moment of failure.
 * - You can override the global policy per call via the `bufferPolicy` parameter of `client.sse(...)`.
 *
 * Usage:
 * ```
 * try {
 *     client.sse("https://example.com/sse", { bufferPolicy(SSEBufferPolicy.LastEvents(5)) }) {
 *         incoming.collect { /* ... */ }
 *     }
 * } catch (e: SSEClientException) {
 *     val text = e.response?.bodyAsText() // contains the last 5 events received
 *     println(text)
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.client.plugins.sse.SSEConfig.sseBufferPolicy)
 */
public fun HttpRequestBuilder.bufferPolicy(policy: SSEBufferPolicy) {
    attributes.put(sseBufferPolicyAttr, policy)
}
