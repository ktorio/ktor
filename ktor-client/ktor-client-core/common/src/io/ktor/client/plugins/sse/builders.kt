/*
* Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.plugins.sse

import io.ktor.client.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.*

/**
 * Installs the [ServerSentEvents] plugin using the [config] as configuration.
 */
public fun HttpClientConfig<*>.ServerSentEvents(config: ServerSentEvents.Config.() -> Unit) {
    install(ServerSentEvents) {
        config()
    }
}

/**
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    block: HttpRequestBuilder.() -> Unit
): ClientServerSentEventsSession {
    plugin(ServerSentEvents)

    val sessionDeferred = CompletableDeferred<ClientServerSentEventsSession>()
    val statement = prepareRequest {
        block()
    }
    try {
        statement.body<ClientServerSentEventsSession, Unit> { session ->
            sessionDeferred.complete(session)
        }
    } catch (cause: Throwable) {
        sessionDeferred.completeExceptionally(ServerSentEventsException(cause))
    }
    return sessionDeferred.await()
}

/**
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientServerSentEventsSession = serverSentEventsSession {
    url(scheme, host, port, path)
    block()
}

/**
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEventsSession(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientServerSentEventsSession = serverSentEventsSession {
    url.takeFrom(urlString)
    block()
}

/**
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEvents(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend ClientServerSentEventsSession.() -> Unit
) {
    val session = serverSentEventsSession(request)
    try {
        block(session)
    } catch (cause: Throwable) {
        throw ServerSentEventsException(cause)
    } finally {
        session.cancel()
    }
}

/**
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEvents(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientServerSentEventsSession.() -> Unit
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
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.serverSentEvents(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientServerSentEventsSession.() -> Unit
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
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sseSession(
    block: HttpRequestBuilder.() -> Unit
): ClientServerSentEventsSession = serverSentEventsSession(block)

/**
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sseSession(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientServerSentEventsSession = serverSentEventsSession(scheme, host, port, path, block)

/**
 * Opens a [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sseSession(
    urlString: String,
    block: HttpRequestBuilder.() -> Unit = {}
): ClientServerSentEventsSession = serverSentEventsSession(urlString, block)

/**
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sse(
    request: HttpRequestBuilder.() -> Unit,
    block: suspend ClientServerSentEventsSession.() -> Unit
): Unit = serverSentEvents(request, block)

/**
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sse(
    scheme: String? = null,
    host: String? = null,
    port: Int? = null,
    path: String? = null,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientServerSentEventsSession.() -> Unit
): Unit = serverSentEvents(scheme, host, port, path, request, block)

/**
 * Opens a [block] with [ClientServerSentEventsSession].
 */
public suspend fun HttpClient.sse(
    urlString: String,
    request: HttpRequestBuilder.() -> Unit = {},
    block: suspend ClientServerSentEventsSession.() -> Unit
): Unit = serverSentEvents(urlString, request, block)
