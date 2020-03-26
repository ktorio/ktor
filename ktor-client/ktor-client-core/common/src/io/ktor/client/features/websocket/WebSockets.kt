/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*

/**
 * Client WebSocket feature.
 *
 * @property pingIntervalMillis - interval between [FrameType.PING] messages.
 * @property timeoutMillis - timeout to receive a [FrameType.PONG] message.
 */
@KtorExperimentalAPI
@OptIn(WebSocketInternalAPI::class)
class WebSockets(
    var pingIntervalMillis: Long = -1L,
    var timeoutMillis: Long = 15000L
) {
    @Suppress("KDocMissingDocumentation")
    companion object Feature : HttpClientFeature<WebSockets, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        override fun prepare(block: WebSockets.() -> Unit): WebSockets = WebSockets().apply {
            block()
        }

        override fun install(feature: WebSockets, scope: HttpClient) {
            scope.requestPipeline.intercept(HttpRequestPipeline.Render) {
                if (!context.url.protocol.isWebsocket()) return@intercept

                proceedWith(WebSocketContent())
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
                if (session !is WebSocketSession) return@intercept
                if (info.type == DefaultClientWebSocketSession::class) {
                    val clientSession: DefaultClientWebSocketSession = with(feature) {
                        DefaultClientWebSocketSession(context, session.asDefault())
                    }

                    proceedWith(HttpResponseContainer(info, clientSession))
                    return@intercept
                }

                val response = HttpResponseContainer(info, DelegatingClientWebSocketSession(context, session))
                proceedWith(response)
            }
        }
    }

    private fun WebSocketSession.asDefault(): DefaultWebSocketSession {
        if (this is DefaultWebSocketSession) return this
        return DefaultWebSocketSession(this, pingIntervalMillis, timeoutMillis)
    }
}

@Suppress("KDocMissingDocumentation")
class WebSocketException(message: String) : IllegalStateException(message)
