package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.cio.*
import java.util.concurrent.*

/**
 * Bind RAW websocket at the current route + [path] optionally checking for websocket [protocol] (ignored if `null`)
 * Requires [WebSockets] feature to be installed first
 *
 * Unlike regular (default) [webSocket], a raw websocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession.incoming] channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled
 *
 * When a websocket session is created, a [handler] lambda will be called with websocket session instance on receiver.
 * Once [handler] function returns, the websocket connection will be terminated immediately. For RAW websockets
 * it is important to perform close sequence properly.
 */
fun Route.webSocketRaw(
    path: String, protocol: String? = null,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    application.feature(WebSockets) // early require

    route(path, HttpMethod.Get) {
        webSocketRaw(protocol, handler)
    }
}

/**
 * Bind RAW websocket at the current route optionally checking for websocket [protocol] (ignored if `null`)
 * Requires [WebSockets] feature to be installed first
 *
 * Unlike regular (default) [webSocket], a raw websocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled
 *
 * When a websocket session is created, a [handler] lambda will be called with websocket session instance on receiver.
 * Once [handler] function returns, the websocket connection will be terminated immediately. For RAW websocket
 * it is important to perform close sequence properly.
 */
fun Route.webSocketRaw(protocol: String? = null, handler: suspend WebSocketServerSession.() -> Unit) {
    application.feature(WebSockets) // early require

    header(HttpHeaders.Connection, "Upgrade") {
        header(HttpHeaders.Upgrade, "websocket") {
            webSocketProtocol(protocol) {
                handle {
                    call.respondWebSocketRaw(protocol) {
                        toServerSession(call).handler()
                    }
                }
            }
        }
    }
}

/**
 * Bind websocket at the current route optionally checking for websocket [protocol] (ignored if `null`)
 * Requires [WebSockets] feature to be installed first
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default websocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames
 *
 * When a websocket session is created, a [handler] lambda will be called with websocket session instance on receiver.
 * Once [handler] function returns, the websocket termination sequence will be scheduled so you shouldn't use
 * [DefaultWebSocketSession] anymore. However websocket could live for a while until close sequence completed or
 * a timeout exceeds
 */
fun Route.webSocket(protocol: String? = null, handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    webSocketRaw(protocol) {
        proceedWebSocket(handler)
    }
}

/**
 * Bind websocket at the current route + [path] optionally checking for websocket [protocol] (ignored if `null`)
 * Requires [WebSockets] feature to be installed first
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default websocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames
 *
 * When a websocket session is created, a [handler] lambda will be called with websocket session instance on receiver.
 * Once [handler] function returns, the websocket termination sequence will be scheduled so you shouldn't use
 * [DefaultWebSocketSession] anymore. However websocket could live for a while until close sequence completed or
 * a timeout exceeds
 */
fun Route.webSocket(path: String, protocol: String? = null, handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    webSocketRaw(path, protocol) {
        proceedWebSocket(handler)
    }
}

// these two functions could be potentially useful for users however it is not clear how to provide them better
// so for now they are still private

private suspend fun ApplicationCall.respondWebSocketRaw(
    protocol: String? = null, handler: suspend WebSocketSession.() -> Unit
) {
    respond(WebSocketUpgrade(this, protocol, handler))
}

private fun Route.webSocketProtocol(protocol: String?, block: Route.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        createChild(WebSocketProtocolsSelector(protocol)).block()
    }
}

@UseExperimental(WebSocketInternalAPI::class)
private suspend fun WebSocketServerSession.proceedWebSocket(handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    val webSockets = application.feature(WebSockets)

    val session =
        DefaultWebSocketSessionImpl(this, webSockets.pingInterval?.toMillis() ?: -1L, webSockets.timeout.toMillis())
    session.run {
        try {
            toServerSession(call).handler()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (io: ChannelIOException) {
            // don't log I/O exceptions
            throw io
        } catch (cause: Throwable) {
            application.log.error("Websocket handler failed", cause)
            throw cause
        }
    }
}

private class WebSocketProtocolsSelector(
    val requiredProtocol: String
) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val protocols = context.call.request.headers[HttpHeaders.SecWebSocketProtocol]
            ?: return RouteSelectorEvaluation.Failed

        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        return RouteSelectorEvaluation.Failed
    }
}
