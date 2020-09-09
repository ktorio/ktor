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
 * @param pingIntervalMillis duration between pings or `null` to disable pings
 * @param timeoutMillis write/ping timeout after that a connection will be closed
 * @param maxFrameSize maximum frame that could be received or sent
 * @param masking whether masking need to be enabled (useful for security)
 */
public class WebSockets(
    public val pingIntervalMillis: Long,
    public val timeoutMillis: Long,
    public val maxFrameSize: Long,
    public val masking: Boolean
) : CoroutineScope {
    private val parent: CompletableJob = Job()

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
        /**
         * Duration between pings or `null` to disable pings
         */
        @Suppress("unused")
        @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
        public var pingPeriod: Duration?
            get() = pingPeriod
            set(new) {
                pingPeriod = new
            }

        /**
         * Duration between pings or `0` to disable pings
         */
        public var pingPeriodMillis: Long = 0

        /**
         * write/ping timeout after that a connection will be closed
         */
        @Suppress("unused")
        @Deprecated("Binary compatibility.", level = DeprecationLevel.HIDDEN)
        public var timeout: Duration
            get() = timeout
            set(new) {
                timeout = new
            }

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
    }

    /**
     * Feature installation object
     */
    public companion object Feature : ApplicationFeature<Application, WebSocketOptions, WebSockets> {
        override val key: AttributeKey<WebSockets> = AttributeKey("WebSockets")

        override fun install(pipeline: Application, configure: WebSocketOptions.() -> Unit): WebSockets {
            val config = WebSocketOptions().also(configure)
            with(config) {
                val webSockets = WebSockets(pingPeriodMillis, timeoutMillis, maxFrameSize, masking)

                pipeline.environment.monitor.subscribe(ApplicationStopPreparing) {
                    webSockets.shutdown()
                }

                pipeline.sendPipeline.intercept(ApplicationSendPipeline.Transform) {
                    if (it is WebSocketUpgrade) {
                        it.call
                    }
                }

                return webSockets
            }
        }
    }
}
