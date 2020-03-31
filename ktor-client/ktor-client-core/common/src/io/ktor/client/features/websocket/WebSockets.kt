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
 * @property pingInterval - interval between [FrameType.PING] messages.
 * @property maxFrameSize - max size of single websocket frame.
 */
@KtorExperimentalAPI
@OptIn(WebSocketInternalAPI::class)
public class WebSockets(
    public val pingInterval: Long = -1L,
    public val maxFrameSize: Long = Int.MAX_VALUE.toLong()
) {

    /**
     * [WebSockets] configuration.
     */
    public class Config {
        /**
         * Sets interval of sending ping frames.
         *
         * Value -1L is for disabled ping.
         */
        public var pingInterval: Long = -1L

        /**
         * Sets maximum frame size in bytes.
         */
        public var maxFrameSize: Long = Int.MAX_VALUE.toLong()
    }

    /**
     * Add WebSockets support for ktor http client.
     */
    public companion object Feature : HttpClientFeature<Config, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        override fun prepare(block: Config.() -> Unit): WebSockets {
            val config = Config().apply(block)
            return WebSockets(config.pingInterval, config.maxFrameSize)
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
        return DefaultWebSocketSession(this, pingInterval, maxFrameSize)
    }
}

@Suppress("KDocMissingDocumentation")
public class WebSocketException(message: String) : IllegalStateException(message)
