package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.time.*
import java.util.*

abstract class WebSocket internal constructor(val call: ApplicationCall, protected val context: PipelineContext<*>) : Closeable {
    private val handlers = ArrayList<(Frame) -> Unit>()
    private val errorHandlers = ArrayList<(Throwable) -> Unit>()
    private val closeHandlers = ArrayList<(CloseReason?) -> Unit>()

    /**
     * Enable or disable masking output messages by a random xor mask.
     * Please note that changing this flag on the fly could be applied to the messages already sent as the sending pipeline works asynchronously
     */
    var masking = false

    /**
     * Specifies frame size limit. Connection will be closed if violated
     */
    @Deprecated("Not yet implemented")
    var maxFrameSize = Long.MAX_VALUE

    var timeout: Duration = Duration.ofSeconds(15)
    abstract var pingInterval: Duration?

    fun handle(handler: (Frame) -> Unit) {
        handlers.add(handler)
    }

    fun handleError(handler: (Throwable) -> Unit) {
        errorHandlers.add(handler)
    }

    fun close(handler: (reason: CloseReason?) -> Unit) {
        closeHandlers.add(handler)
    }

    abstract fun send(frame: Frame)

    override fun close(): Nothing {
        call.close() // TODO move call.close() to some generic point
        context.finishAll()
    }

    fun close(reason: CloseReason) {
        send(Frame.Close(buildByteBuffer {
            putShort(reason.code)
            putString(reason.message, Charsets.UTF_8)
        }))
    }

    protected open fun frameHandler(frame: Frame) {
        handlers.forEach { it(frame) }
    }

    protected open fun closeHandler(reason: CloseReason?) {
        closeHandlers.forEach { it(reason) }
    }
}

fun RoutingEntry.webSocket(path: String, protocol: String? = null, configure: WebSocket.() -> Unit) {
    route(HttpMethod.Get, path) {
        header(HttpHeaders.Connection, "Upgrade") {
            header(HttpHeaders.Upgrade, "websocket") {
                webSocketProtocol(protocol) {
                    handle {
                        val extensions = call.request.header(HttpHeaders.SecWebSocketExtensions)

                        call.respond(WebSocketUpgrade(call, protocol, configure))
                    }
                }
            }
        }
    }
}

private fun RoutingEntry.webSocketProtocol(protocol: String?, block: RoutingEntry.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        select(WebSocketProtocolsSelector(protocol)).block()
    }
}

private class WebSocketProtocolsSelector(val requiredProtocol: String) : RoutingSelector {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val protocols = context.headers[HttpHeaders.SecWebSocketProtocol] ?: return RouteSelectorEvaluation(true, 1.0)
        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation(true, 1.0)
        }

        return RouteSelectorEvaluation.Failed
    }
}