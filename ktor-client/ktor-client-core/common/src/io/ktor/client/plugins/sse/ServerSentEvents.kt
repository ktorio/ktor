/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.sse

import io.ktor.client.engine.*
import io.ktor.client.plugins.api.*
import io.ktor.client.statement.*
import io.ktor.util.logging.*

internal val LOGGER = KtorSimpleLogger("io.ktor.client.plugins.sse.ServerSentEvents")

/**
 * Indicates if a client engine supports Server-sent events.
 */
public object ServerSentEventsCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "ServerSentEventsCapability"
}

/**
 * Client Server-sent events plugin.
 */
public val ServerSentEvents: ClientPlugin<ServerSentEventsConfig> = createClientPlugin(
    "ServerSentEvents",
    ::ServerSentEventsConfig
) {
    val reconnectionTime = pluginConfig.reconnectionTime

    transformRequestBody { request, _, _ ->
        LOGGER.trace("Sending Server-sent events request ${request.url}")
        request.setCapability(ServerSentEventsCapability, Unit)

        ServerSentEventsContent(reconnectionTime)
    }

    transformResponseBody { response, content, requestedType ->
        if (content !is ClientServerSentEventsSession) {
            LOGGER.trace("Skipping non Server-sent events response from ${response.request.url}: $content")
            content
        } else {
            LOGGER.trace("Receive Server-sent events session from ${response.request.url}: $content")
            HttpResponseContainer(requestedType, content)
        }
    }
}
