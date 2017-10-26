package io.ktor.websocket

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*

fun Route.webSocketRaw(protocol: String? = null, handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    application.feature(WebSockets) // early require

    header(HttpHeaders.Connection, "Upgrade") {
        header(HttpHeaders.Upgrade, "websocket") {
            webSocketProtocol(protocol) {
                handle {
                    call.respondWebSocketRaw(protocol, handler)
                }
            }
        }
    }
}

fun Route.webSocketRaw(path: String, protocol: String? = null, handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    application.feature(WebSockets) // early require

    route(path, HttpMethod.Get) {
        webSocketRaw(protocol, handler)
    }
}

fun Route.webSocket(protocol: String? = null, handler: suspend DefaultWebSocketSession.() -> Unit) {
    webSocketRaw(protocol) { dispatchers ->
        proceedWebSocket(dispatchers, handler)
    }
}

fun Route.webSocket(path: String, protocol: String? = null, handler: suspend DefaultWebSocketSession.() -> Unit) {
    webSocketRaw(path, protocol) { dispatchers ->
        proceedWebSocket(dispatchers, handler)
    }
}

// these two functions could be potentially useful for users however it is not clear how to provide them better
// so for now they are still private

private suspend fun ApplicationCall.respondWebSocketRaw(protocol: String? = null, handler: suspend WebSocketSession.(WebSocketUpgrade.Dispatchers) -> Unit) {
    respond(WebSocketUpgrade(this, protocol, handler))
}

private suspend fun WebSocketSession.proceedWebSocket(dispatchers: WebSocketUpgrade.Dispatchers, handler: suspend DefaultWebSocketSession.() -> Unit) {
    val webSockets = application.feature(WebSockets)

    val raw = this
    val ws = DefaultWebSocketSessionImpl(raw, dispatchers.engineContext, dispatchers.userContext, NoPool)
    ws.pingInterval = webSockets.pingInterval
    ws.timeout = webSockets.timeout

    ws.run(handler)
}

private fun Route.webSocketProtocol(protocol: String?, block: Route.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        createChild(WebSocketProtocolsSelector(protocol)).block()
    }
}

private class WebSocketProtocolsSelector(val requiredProtocol: String) : RouteSelector(RouteSelectorEvaluation.qualityConstant) {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val protocols = context.call.request.headers[HttpHeaders.SecWebSocketProtocol] ?: return RouteSelectorEvaluation.Failed
        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        return RouteSelectorEvaluation.Failed
    }
}



