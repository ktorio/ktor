/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.websocket

import io.ktor.serialization.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal val LOGGER = KtorSimpleLogger("io.ktor.server.websocket.WebSockets")

/**
 * WebSockets support plugin. It is required to be installed first before binding any websocket endpoints
 *
 * ```
 * install(WebSockets)
 *
 * install(Routing) {
 *     webSocket("/ws") {
 *          incoming.consumeForEach { ... }
 *     }
 * }
 * ```
 *
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets)
 *
 * @param pingIntervalMillis duration between pings or [PINGER_DISABLED] to disable pings.
 * @param timeoutMillis write/ping timeout after that a connection will be closed.
 * @param maxFrameSize maximum frame that could be received or sent.
 * @param masking whether masking need to be enabled (useful for security).
 * @param extensionsConfig is configuration for WebSocket extensions.
 */
public class WebSockets private constructor(
    public val pingIntervalMillis: Long,
    public val timeoutMillis: Long,
    public val maxFrameSize: Long,
    public val masking: Boolean,
    public val extensionsConfig: WebSocketExtensionsConfig,
    public val contentConverter: WebsocketContentConverter?
) : CoroutineScope {
    private val parent: CompletableJob = Job()

    public constructor(
        pingIntervalMillis: Long,
        timeoutMillis: Long,
        maxFrameSize: Long,
        masking: Boolean
    ) : this(pingIntervalMillis, timeoutMillis, maxFrameSize, masking, WebSocketExtensionsConfig(), null)

    override val coroutineContext: CoroutineContext
        get() = parent

    init {
        require(pingIntervalMillis >= 0)
        require(timeoutMillis >= 0)
        require(maxFrameSize > 0)
    }

    private fun shutdown() {
        parent.complete()
    }

    /**
     * Websockets configuration options
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions)
     */
    @KtorDsl
    public class WebSocketOptions {
        internal val extensionsConfig = WebSocketExtensionsConfig()

        /**
         * Duration between pings or [PINGER_DISABLED] to disable pings
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.pingPeriodMillis)
         */
        public var pingPeriodMillis: Long = PINGER_DISABLED

        /**
         * write/ping timeout after that a connection will be closed
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.timeoutMillis)
         */
        public var timeoutMillis: Long = 15_000L

        /**
         * Maximum frame that could be received or sent
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.maxFrameSize)
         */
        public var maxFrameSize: Long = Long.MAX_VALUE

        /**
         * Whether masking need to be enabled (useful for security)
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.masking)
         */
        public var masking: Boolean = false

        /**
         * A converter for serialization/deserialization
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.contentConverter)
         */
        public var contentConverter: WebsocketContentConverter? = null

        /**
         * Configure WebSocket extensions.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.WebSocketOptions.extensions)
         */
        public fun extensions(block: WebSocketExtensionsConfig.() -> Unit) {
            extensionsConfig.apply(block)
        }
    }

    /**
     * Plugin installation object.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.Plugin)
     */
    public companion object Plugin : BaseApplicationPlugin<Application, WebSocketOptions, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("WebSockets")

        /**
         * Key for saving configured WebSocket extensions for the specific call.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.WebSockets.Plugin.EXTENSIONS_KEY)
         */
        public val EXTENSIONS_KEY: AttributeKey<List<WebSocketExtension<*>>> =
            AttributeKey("WebSocket extensions")

        override fun install(pipeline: Application, configure: WebSocketOptions.() -> Unit): WebSockets {
            val config = WebSocketOptions().also(configure)
            with(config) {
                val webSockets = WebSockets(
                    pingPeriodMillis,
                    timeoutMillis,
                    maxFrameSize,
                    masking,
                    extensionsConfig,
                    contentConverter
                )

                pipeline.monitor.subscribe(ApplicationStopPreparing) {
                    LOGGER.trace("Shutdown WebSockets due to application stop")
                    webSockets.shutdown()
                }

                pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) {
                    if (it !is WebSocketUpgrade) return@intercept
                }

                return webSockets
            }
        }
    }
}
