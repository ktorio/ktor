/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.websocket

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.util.*
import kotlin.native.concurrent.*

@ExperimentalWebSocketExtensionApi
@SharedImmutable
private val REQUEST_EXTENSIONS_KEY = AttributeKey<List<WebSocketExtension<*>>>("Websocket extensions")

/**
 * Indicates if a client engine supports WebSockets.
 */
public object WebSocketCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "WebSocketCapability"
}

/**
 * Indicates if a client engine supports extensions for WebSocket feature.
 */
public object WebSocketExtensionsCapability : HttpClientEngineCapability<Unit> {
    override fun toString(): String = "WebSocketExtensionsCapability"
}

/**
 * Client WebSocket feature.
 *
 * @property pingInterval - interval between [FrameType.PING] messages.
 * @property maxFrameSize - max size of single websocket frame.
 * @property extensionsConfig - extensions configuration
 */
@OptIn(WebSocketInternalAPI::class)
public class WebSockets @OptIn(ExperimentalWebSocketExtensionApi::class)
internal constructor(
    public val pingInterval: Long,
    public val maxFrameSize: Long,
    private val extensionsConfig: WebSocketExtensionsConfig
) {
    /**
     * Client WebSocket feature.
     *
     * @property pingInterval - interval between [FrameType.PING] messages.
     * @property maxFrameSize - max size of single websocket frame.
     */
    @OptIn(ExperimentalWebSocketExtensionApi::class)
    public constructor(
        pingInterval: Long = -1L,
        maxFrameSize: Long = Int.MAX_VALUE.toLong(),
    ) : this(pingInterval, maxFrameSize, WebSocketExtensionsConfig())

    /**
     * Client WebSocket feature.
     */
    @OptIn(ExperimentalWebSocketExtensionApi::class)
    public constructor() : this(-1L, Int.MAX_VALUE.toLong(), WebSocketExtensionsConfig())

    @ExperimentalWebSocketExtensionApi
    private fun installExtensions(context: HttpRequestBuilder) {
        val installed = extensionsConfig.build()
        context.attributes.put(REQUEST_EXTENSIONS_KEY, installed)

        val protocols = installed.flatMap { it.protocols }
        addNegotiatedProtocols(context, protocols)
    }

    @Suppress("UNCHECKED_CAST")
    @ExperimentalWebSocketExtensionApi
    private fun completeNegotiation(
        call: HttpClientCall
    ): List<WebSocketExtension<*>> {
        val serverExtensions: List<WebSocketExtensionHeader> = call.response
            .headers[HttpHeaders.SecWebSocketExtensions]
            ?.let { parseWebSocketExtensions(it) } ?: emptyList()

        val clientExtensions = call.attributes[REQUEST_EXTENSIONS_KEY]

        return clientExtensions.filter { it.clientNegotiation(serverExtensions) }
    }

    @OptIn(ExperimentalWebSocketExtensionApi::class)
    private fun addNegotiatedProtocols(context: HttpRequestBuilder, protocols: List<WebSocketExtensionHeader>) {
        val headerValue = protocols.joinToString(";")
        context.header(HttpHeaders.SecWebSocketExtensions, headerValue)
    }

    internal fun convertSessionToDefault(session: WebSocketSession): DefaultWebSocketSession {
        if (session is DefaultWebSocketSession) return session

        return DefaultWebSocketSession(session, pingInterval, timeoutMillis = pingInterval * 2).also {
            it.maxFrameSize = this@WebSockets.maxFrameSize
        }
    }

    init {
    }

    /**
     * [WebSockets] configuration.
     */
    public class Config {
        @ExperimentalWebSocketExtensionApi
        internal val extensionsConfig: WebSocketExtensionsConfig = WebSocketExtensionsConfig()

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

        /**
         * Configure WebSocket extensions.
         */
        @ExperimentalWebSocketExtensionApi
        public fun extensions(block: WebSocketExtensionsConfig.() -> Unit) {
            extensionsConfig.apply(block)
        }
    }

    /**
     * Add WebSockets support for ktor http client.
     */
    public companion object Feature : HttpClientFeature<Config, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("Websocket")

        @OptIn(ExperimentalWebSocketExtensionApi::class)
        override fun prepare(block: Config.() -> Unit): WebSockets {
            val config = Config().apply(block)
            return WebSockets(config.pingInterval, config.maxFrameSize, config.extensionsConfig)
        }

        @OptIn(ExperimentalWebSocketExtensionApi::class)
        override fun install(feature: WebSockets, scope: HttpClient) {
            val extensionsSupported = scope.engine.supportedCapabilities.contains(WebSocketExtensionsCapability)

            scope.requestPipeline.intercept(HttpRequestPipeline.Render) {
                if (!context.url.protocol.isWebsocket()) return@intercept
                context.setCapability(WebSocketCapability, Unit)

                if (extensionsSupported) {
                    feature.installExtensions(context)
                }

                proceedWith(WebSocketContent())
            }

            scope.responsePipeline.intercept(HttpResponsePipeline.Transform) { (info, session) ->
                if (session !is WebSocketSession) return@intercept

                val clientSession: ClientWebSocketSession = when (info.type) {
                    DefaultClientWebSocketSession::class -> {
                        val defaultSession = feature.convertSessionToDefault(session)
                        val clientSession = DefaultClientWebSocketSession(context, defaultSession)

                        val negotiated = if (extensionsSupported) {
                            feature.completeNegotiation(context)
                        } else emptyList()

                        clientSession.apply {
                            start(negotiated)
                        }
                    }
                    else -> DelegatingClientWebSocketSession(context, session)
                }

                val response = HttpResponseContainer(info, clientSession)
                proceedWith(response)
            }
        }
    }
}

@Suppress("KDocMissingDocumentation")
public class WebSocketException(message: String) : IllegalStateException(message)
