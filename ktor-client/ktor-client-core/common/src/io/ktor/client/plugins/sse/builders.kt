/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.sse.*
import kotlinx.coroutines.*

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
public suspend fun HttpClient.serverSentEventsSession(
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession {
    plugin(SSE)

    val sessionDeferred = CompletableDeferred<ClientSSESession>()
    val statement = prepareRequest {
        block()
    }
    try {
        statement.body<ClientSSESession, Unit> { session ->
            sessionDeferred.complete(session)
        }
    } catch (cause: Throwable) {
        sessionDeferred.completeExceptionally(SSEException(cause))
    }
    return sessionDeferred.await()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend ClientSSESession.() -> Unit
) {
    val session = serverSentEventsSession(request)
    try {
        block(session)
    } catch (cause: Throwable) {
        throw SSEException(cause)
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
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url(scheme, host, port, path)
            request()
        },
        block
    )
}

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
) {
    serverSentEvents(
        {
            url.takeFrom(urlString)
            request()
        },
        block
    )
}

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    block: HttpRequestBuilder.() -> Unit
): ClientSSESession = serverSentEventsSession(block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(scheme, host, port, path, block)

/**
 * Opens a [ClientSSESession].
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientSSESession = serverSentEventsSession(urlString, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(request, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(scheme, host, port, path, request, block)

/**
 * Opens a [block] with [ClientSSESession].
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientSSESession.() -> Unit
): Unit = serverSentEvents(urlString, request, block)
