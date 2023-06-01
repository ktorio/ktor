/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.okhttp

import io.ktor.client.plugins.sse.*
import io.ktor.sse.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import okhttp3.*
import okhttp3.sse.*
import kotlin.coroutines.*

internal class OkHttpSSESession(
    engine: OkHttpClient,
    engineRequest: Request,
    override val coroutineContext: CoroutineContext,
) : ClientSSESession, EventSourceListener() {
    private val serverSentEventsSource = EventSources.createFactory(engine).newEventSource(engineRequest, this)

    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()

    private val _incoming = Channel<ServerSentEvent>(Channel.UNLIMITED)

    override val incoming: ReceiveChannel<ServerSentEvent>
        get() = _incoming

    override fun onOpen(eventSource: EventSource, response: Response) {
        originResponse.complete(response)
    }

    override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
        _incoming.trySend(ServerSentEvent(type, id, data))
    }

    override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
        val error = t?.let { SSEException(it) }
            ?: SSEException("Unexpected error occurred")
        originResponse.completeExceptionally(error)
        _incoming.close()
        serverSentEventsSource.cancel()
    }

    override fun onClosed(eventSource: EventSource) {
        _incoming.close()
        serverSentEventsSource.cancel()
    }
}
