/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.websocket

import io.ktor.application.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*

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
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, negotiateExtensions = false, handler)
}

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
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    application.feature(WebSockets) // early require

    route(path, HttpMethod.Get) {
        webSocketRaw(protocol, negotiateExtensions, handler)
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
public fun Route.webSocketRaw(protocol: String? = null, handler: suspend WebSocketServerSession.() -> Unit) {
    webSocketRaw(protocol, negotiateExtensions = false, handler)
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
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun Route.webSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
) {
    application.feature(WebSockets) // early require

    header(HttpHeaders.Connection, "Upgrade") {
        header(HttpHeaders.Upgrade, "websocket") {
            webSocketProtocol(protocol) {
                handle {
                    call.respondWebSocketRaw(protocol, negotiateExtensions) {
                        toServerSession(call).handler()
                    }
                }
            }
        }
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
@Deprecated(
    "Use webSocketRaw(protocol = protocol, handler = handler) instead.",
    ReplaceWith("webSocketRaw(protocol = webSocketProtocol, handler = webSocketHandler)")
)
public fun Route.webSocketRaw(
    webSocketProtocol: String,
    webSocketHandler: suspend WebSocketServerSession.() -> Unit,
    nothing: Nothing? = null
) {
    webSocketRaw(protocol = webSocketProtocol, handler = webSocketHandler)
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
public fun Route.webSocket(protocol: String? = null, handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    webSocketRaw(protocol, negotiateExtensions = true) {
        proceedWebSocket(handler)
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
@Deprecated(
    "Use webSocket(protocol = protocol, handler = handler) instead.",
    ReplaceWith("webSocket(protocol = webSocketProtocol, handler = webSocketHandler)")
)
public fun Route.webSocket(
    webSocketProtocol: String,
    webSocketHandler: suspend DefaultWebSocketServerSession.() -> Unit,
    @Suppress("UNUSED_PARAMETER")
    nothing: Nothing? = null
) {
    webSocket(protocol = webSocketProtocol, handler = webSocketHandler)
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
public fun Route.webSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    webSocketRaw(path, protocol, negotiateExtensions = true) {
        proceedWebSocket(handler)
    }
}

// these two functions could be potentially useful for users however it is not clear how to provide them better
// so for now they are still private

private suspend fun ApplicationCall.respondWebSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketSession.() -> Unit
) {
    respond(WebSocketUpgrade(this, protocol, negotiateExtensions, handler))
}

private fun Route.webSocketProtocol(protocol: String?, block: Route.() -> Unit) {
    if (protocol == null) {
        block()
    } else {
        createChild(WebSocketProtocolsSelector(protocol)).block()
    }
}

@OptIn(WebSocketInternalAPI::class, ExperimentalWebSocketExtensionApi::class)
private suspend fun WebSocketServerSession.proceedWebSocket(handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    val webSockets = application.feature(WebSockets)

    val session = DefaultWebSocketSessionImpl(
        this,
        webSockets.pingInterval?.toMillis() ?: -1L,
        webSockets.timeout.toMillis()
    ).apply {
        val extensions = call.attributes[WebSockets.EXTENSIONS_KEY]
        start(extensions)
    }

    session.handleServerSession(call, handler)
    session.joinSession()
}

private suspend fun CoroutineScope.joinSession() {
    coroutineContext[Job]!!.join()
}

@OptIn(WebSocketInternalAPI::class)
private suspend fun DefaultWebSocketSessionImpl.handleServerSession(
    call: ApplicationCall,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    try {
        val serverSession = toServerSession(call)
        handler(serverSession)
        close()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (io: ChannelIOException) {
        // don't log I/O exceptions
        throw io
    } catch (cause: Throwable) {
        call.application.log.error("Websocket handler failed", cause)
        throw cause
    }
}

private class WebSocketProtocolsSelector(
    val requiredProtocol: String
) : RouteSelector() {
    override fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val protocols = context.call.request.headers[HttpHeaders.SecWebSocketProtocol]
            ?: return RouteSelectorEvaluation.Failed

        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        return RouteSelectorEvaluation.Failed
    }
}
