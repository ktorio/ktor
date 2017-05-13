package org.jetbrains.ktor.websocket

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.time.*
import java.util.*
import java.util.concurrent.atomic.*

abstract class WebSocket internal constructor(val call: ApplicationCall) : Closeable {
    val application: Application = call.application

    private val handlers = ArrayList<suspend (Frame) -> Unit>()
    private val errorHandlers = ArrayList<(Throwable) -> Unit>()
    private val closeHandlers = ArrayList<suspend (CloseReason?) -> Unit>()
    private val closedLatch = AsyncCountDownLatch(1)
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

    fun handle(handler: suspend (Frame) -> Unit) {
        handlers.add(handler)
    }

    fun handleError(handler: (Throwable) -> Unit) {
        errorHandlers.add(handler)
    }

    fun close(handler: suspend (reason: CloseReason?) -> Unit) {
        closeHandlers.add(handler)
    }

    abstract fun enqueue(frame: Frame)
    abstract suspend fun flush()
    abstract suspend fun send(frame: Frame)

    suspend fun close(reason: CloseReason) {
        send(Frame.Close(buildByteBuffer {
            putShort(reason.code)
            putString(reason.message, Charsets.UTF_8)
        }))
        awaitClose()
    }

    suspend fun awaitClose() {
        closedLatch.await()
    }

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
            try {
                closeHandlers.forEach { it(reason) }
            } finally {
                closedLatch.countDown()
            }
        }
    }
}

fun Route.webSocket(path: String, protocol: String? = null, configure: suspend WebSocket.() -> Unit) {
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