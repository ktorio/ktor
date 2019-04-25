package io.ktor.websocket

import io.ktor.application.*
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
 * @param pingInterval duration between pings or `null` to disable pings
 * @param timeout write/ping timeout after that a connection will be closed
 * @param maxFrameSize maximum frame that could be received or sent
 * @param masking whether masking need to be enabled (useful for security)
 */
class WebSockets(
    val pingInterval: Duration?,
    val timeout: Duration,
    val maxFrameSize: Long,
    val masking: Boolean
) : CoroutineScope {
    private val parent: CompletableJob = Job()

    override val coroutineContext: CoroutineContext
        get() = parent

    private fun shutdown() {
        parent.complete()
    }

    /**
     * Websockets configuration options
     */
    class WebSocketOptions {
        /**
         * Duration between pings or `null` to disable pings
         */
        var pingPeriod: Duration? = null

        /**
         * write/ping timeout after that a connection will be closed
         */
        var timeout: Duration = Duration.ofSeconds(15)

        /**
         * Maximum frame that could be received or sent
         */
        var maxFrameSize: Long = Long.MAX_VALUE

        /**
         * Whether masking need to be enabled (useful for security)
         */
        var masking: Boolean = false
    }

    /**
     * Feature installation object
     */
    companion object Feature : ApplicationFeature<Application, WebSocketOptions, WebSockets> {
        override val key = AttributeKey<WebSockets>("WebSockets")

        override fun install(pipeline: Application, configure: WebSocketOptions.() -> Unit): WebSockets {
            val config = WebSocketOptions().also(configure)
            with(config) {
                val webSockets = WebSockets(pingPeriod, timeout, maxFrameSize, masking)

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
