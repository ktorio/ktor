/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import java.time.*
import kotlin.coroutines.*

/**
 * WebSockets support feature. It is required to be installed first before binding any websocket endpoints
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
 * @param pingIntervalMillis duration between pings or `null` to disable pings.
 * @param timeoutMillis write/ping timeout after that a connection will be closed.
 * @param maxFrameSize maximum frame that could be received or sent.
 * @param masking whether masking need to be enabled (useful for security).
 * @param extensionsConfig is configuration for WebSocket extensions.
 */
public class WebSockets @ExperimentalWebSocketExtensionApi constructor(
    public val pingIntervalMillis: Long,
    public val timeoutMillis: Long,
    public val maxFrameSize: Long,
    public val masking: Boolean,
    public val extensionsConfig: WebSocketExtensionsConfig
) : CoroutineScope {
    private val parent: CompletableJob = Job()

    @OptIn(ExperimentalWebSocketExtensionApi::class)
    public constructor(
        pingIntervalMillis: Long,
        timeoutMillis: Long,
        maxFrameSize: Long,
        masking: Boolean
    ) : this(pingIntervalMillis, timeoutMillis, maxFrameSize, masking, WebSocketExtensionsConfig())

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
     */
    public class WebSocketOptions {
        @ExperimentalWebSocketExtensionApi
        internal val extensionsConfig = WebSocketExtensionsConfig()

        /**
         * Duration between pings or `0` to disable pings
         */
        public var pingPeriodMillis: Long = 0

        /**
         * write/ping timeout after that a connection will be closed
         */
        public var timeoutMillis: Long = 15000L

        /**
         * Maximum frame that could be received or sent
         */
        public var maxFrameSize: Long = Long.MAX_VALUE

        /**
         * Whether masking need to be enabled (useful for security)
         */
        public var masking: Boolean = false

        /**
         * Configure WebSocket extensions.
         */
        @ExperimentalWebSocketExtensionApi
        public fun extensions(block: WebSocketExtensionsConfig.() -> Unit) {
            extensionsConfig.apply(block)
        }
    }

    /**
     * Feature installation object.
     */
    public companion object Feature : ApplicationFeature<Application, WebSocketOptions, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("WebSockets")

        /**
         * Key for saving configured WebSocket extensions for the specific call.
         */
        @ExperimentalWebSocketExtensionApi
        public val EXTENSIONS_KEY: AttributeKey<List<WebSocketExtension<*>>> =
            AttributeKey("WebSocket extensions")

        override fun install(pipeline: Application, configure: WebSocketOptions.() -> Unit): WebSockets {
            val config = WebSocketOptions().also(configure)
            with(config) {
                @OptIn(ExperimentalWebSocketExtensionApi::class)
                val webSockets = WebSockets(
                    pingPeriodMillis,
                    timeoutMillis,
                    maxFrameSize,
                    masking,
                    extensionsConfig
                )

                pipeline.environment.monitor.subscribe(ApplicationStopPreparing) {
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
