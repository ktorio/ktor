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
 */
class WebSockets(
    val pingInterval: Duration?,
    val timeout: Duration,
    val maxFrameSize: Long,
    val masking: Boolean
): CoroutineScope {
    private val parent = CompletableDeferred<Unit>()

    override val coroutineContext: CoroutineContext
        get() = parent

    @Deprecated("Use websockets feature instance as CoroutineScope instead")
    val context: CompletableDeferred<Unit> get() = parent

    private fun shutdown() {
        parent.complete(Unit)
    }

    class WebSocketOptions {
        var pingPeriod: Duration? = null
        var timeout: Duration = Duration.ofSeconds(15)
        var maxFrameSize: Long = Long.MAX_VALUE
        var masking: Boolean = false
    }

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