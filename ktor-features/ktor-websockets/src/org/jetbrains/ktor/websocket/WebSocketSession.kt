package org.jetbrains.ktor.websocket

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.time.*
import java.util.*
import java.util.concurrent.atomic.*

abstract class WebSocketSession internal constructor(val call: ApplicationCall) {
    val application: Application = call.application

    private val handlers = ArrayList<suspend (Frame) -> Unit>()
    private val errorHandlers = ArrayList<(Throwable) -> Unit>()
    private val closeHandlers = ArrayList<suspend (CloseReason?) -> Unit>()
    private val closedNotified = AtomicBoolean()

    /**
     * Enable or disable masking output messages by a random xor mask.
     * Please note that changing this flag on the fly could be applied to the messages already sent as the sending pipeline works asynchronously
     */
    open var masking = false

    /**
     * Specifies frame size limit. Connection will be closed if violated
     */
    var maxFrameSize = Long.MAX_VALUE

    var timeout: Duration = Duration.ofSeconds(15)
    abstract var pingInterval: Duration?

    fun handle(handler: suspend (Frame) -> Unit): DisposableHandle {
        handlers.add(handler)

        return object: DisposableHandle {
            override fun dispose() {
                handlers.remove(handler)
            }
        }
    }

    fun handleError(handler: (Throwable) -> Unit) {
        errorHandlers.add(handler)
    }

    fun close(handler: suspend (reason: CloseReason?) -> Unit) {
        closeHandlers.add(handler)
    }

    @Deprecated("Use send instead", ReplaceWith("send(frame)"))
    fun enqueue(frame: Frame) {
        if (frame.frameType.controlFrame) {
            throw IllegalArgumentException("You should never enqueue control frames as they are delivery-time sensitive, use send() instead")
        }

        runBlocking {
            send(frame)
        }
    }

    abstract suspend fun flush()
    abstract suspend fun send(frame: Frame)

    suspend fun close(reason: CloseReason) {
        send(Frame.Close(reason))
        awaitClose()
    }

    abstract suspend fun awaitClose()

    protected suspend open fun frameHandler(frame: Frame) {
        if (!closedNotified.get()) {
            handlers.forEach { it(frame) }
        }
    }

    protected suspend open fun errorHandler(t: Throwable) {
        errorHandlers.forEach {
            try {
                it(t)
            } catch (sub: Throwable) {
                t.addSuppressed(sub)
            }
        }
    }

    protected suspend open fun closeHandler(reason: CloseReason?) {
        if (closedNotified.compareAndSet(false, true)) {
            closeHandlers.forEach { it(reason) }
        }
    }

    internal open fun terminate() {
    }
}

@Deprecated("Use WebSocketSession instead", ReplaceWith("WebSocketSession"))
typealias WebSocket = WebSocketSession

fun Route.webSocket(path: String, protocol: String? = null, configure: suspend WebSocketSession.() -> Unit) {
    try {
        application.feature(WebSockets)
    } catch (ifMissing: Throwable) {
        application.install(WebSockets)
    }

    route(HttpMethod.Get, path) {
        header(HttpHeaders.Connection, "Upgrade") {
            header(HttpHeaders.Upgrade, "websocket") {
                webSocketProtocol(protocol) {
                    handle {
                        //val extensions = call.request.header(HttpHeaders.SecWebSocketExtensions)

                        call.respond(WebSocketUpgrade(call, protocol, configure))
                    }
                }
            }
        }
    }
}

private fun Route.webSocketProtocol(protocol: String?, block: Route.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        select(WebSocketProtocolsSelector(protocol)).block()
    }
}

private class WebSocketProtocolsSelector(val requiredProtocol: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, index: Int): RouteSelectorEvaluation {
        val protocols = context.headers[HttpHeaders.SecWebSocketProtocol] ?: return RouteSelectorEvaluation.Failed
        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        return RouteSelectorEvaluation.Failed
    }
}