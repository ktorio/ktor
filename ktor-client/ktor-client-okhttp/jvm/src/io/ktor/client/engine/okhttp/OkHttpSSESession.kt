/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.plugins.sse.*
import io.ktor.http.*
import io.ktor.sse.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import kotlin.coroutines.CoroutineContext

internal class OkHttpSSESession(
    engine: OkHttpClient,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext,
) : SSESession, EventSourceListener() {
    private val serverSentEventsSource = EventSources.createFactory(engine).newEventSource(engineRequest, this)

    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()

    private val _incoming = Channel<ServerSentEvent>(8)

    override val incoming: Flow<ServerSentEvent>
        get() = _incoming.receiveAsFlow()

    override fun onOpen(eventSource: EventSource, response: Response) {
        originResponse.complete(response)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        _incoming.trySendBlocking(ServerSentEvent(data, type, id))
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val statusCode = response?.code
        val contentType = response?.headers?.get(HttpHeaders.ContentType)

        if (response != null &&
            (statusCode != HttpStatusCode.OK.value || contentType != ContentType.Text.EventStream.toString())
        ) {
            originResponse.complete(response)
        } else {
            val error = t?.let {
                SSEClientException(
                    message = "Exception during OkHttpSSESession: ${it.message}",
                    cause = it
                )
            } ?: mapException(response)
            originResponse.completeExceptionally(error)
        }

        _incoming.close()
        serverSentEventsSource.cancel()
    }

    override fun onClosed(eventSource: EventSource) {
        _incoming.close()
        serverSentEventsSource.cancel()
    }

    private fun mapException(response: Response?): SSEClientException {
        fun unexpectedError() = SSEClientException(message = "Unexpected error occurred in OkHttpSSESession")

        return when {
            response == null -> unexpectedError()

            response.code != HttpStatusCode.OK.value ->
                SSEClientException(message = "Expected status code ${HttpStatusCode.OK.value} but was ${response.code}")

            response.headers[HttpHeaders.ContentType]
                ?.let { ContentType.parse(it) }?.withoutParameters() != ContentType.Text.EventStream ->
                @Suppress("ktlint:standard:max-line-length")
                SSEClientException(
                    message = "Content type must be ${ContentType.Text.EventStream} but was ${response.headers[HttpHeaders.ContentType]}"
                )

            else -> unexpectedError()
        }
    }
}
