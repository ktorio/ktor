package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.util.*
import java.time.*

class WebSockets(
        val pingInterval: Duration?,
        val timeout: Duration,
        val maxFrameSize: Long,
        val masking: Boolean
) {
    class WebSocketOptions {
        var pingPeriod: Duration? = null
        var timeout: Duration = Duration.ofSeconds(15)
        var maxFrameSize = Long.MAX_VALUE
        var masking: Boolean = false
    }

    companion object : ApplicationFeature<Application, WebSocketOptions, WebSockets> {
        override val key = AttributeKey<WebSockets>("WebSockets")

        override fun install(pipeline: Application, configure: WebSocketOptions.() -> Unit): WebSockets {
            return WebSocketOptions().also(configure).let { options ->
                val webSockets = WebSockets(options.pingPeriod, options.timeout, options.maxFrameSize, options.masking)

                webSockets
            }
        }
    }
}