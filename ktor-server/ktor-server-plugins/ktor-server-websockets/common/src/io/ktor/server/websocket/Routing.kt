/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:kotlin.jvm.JvmMultifileClass
@file:kotlin.jvm.JvmName("RoutingKt")

package io.ktor.server.websocket

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.ktor.utils.io.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.SelectClause1
import kotlin.concurrent.Volatile

/**
 * Binds RAW WebSocket at the current route + [path] optionally checking the for WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession.incoming] channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSockets
 * it is important to perform close sequence properly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocketRaw)
 */
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    handler: suspend WebSocketServerSession.() -> Unit
): Route {
    return webSocketRaw(path, protocol, negotiateExtensions = false, handler)
}

/**
 * Bind RAW WebSocket at the current route + [path] optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession.incoming] channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSockets
 * it is important to perform close sequence properly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocketRaw)
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun Route.webSocketRaw(
    path: String,
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
): Route {
    plugin(WebSockets) // early require

    return route(path, HttpMethod.Get) {
        webSocketRaw(protocol, negotiateExtensions, handler)
    }
}

/**
 * Bind RAW WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSocket
 * it is important to perform close sequence properly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocketRaw)
 */
public fun Route.webSocketRaw(protocol: String? = null, handler: suspend WebSocketServerSession.() -> Unit): Route {
    return webSocketRaw(protocol, negotiateExtensions = false, handler)
}

/**
 * Bind RAW WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * Unlike regular (default) [webSocket], a raw WebSocket is not handling any ping/pongs, timeouts or close frames.
 * So [WebSocketSession]'s incoming channel will contain all low-level control frames and all fragmented frames need
 * to be reassembled.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket connection will be terminated immediately. For RAW WebSocket
 * it is important to perform close sequence properly.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocketRaw)
 *
 * @param negotiateExtensions indicates if the server should negotiate installed WebSocket extensions.
 */
public fun Route.webSocketRaw(
    protocol: String? = null,
    negotiateExtensions: Boolean = false,
    handler: suspend WebSocketServerSession.() -> Unit
): Route {
    plugin(WebSockets) // early require

    return header(HttpHeaders.Connection, "Upgrade") {
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
 * Bind WebSocket at the current route optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default WebSocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames.
 *
 * When a WebSocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the websocket termination sequence will be scheduled, so you shouldn't use
 * [DefaultWebSocketSession] anymore. However, WebSocket could live for a while until close sequence completed or
 * a timeout exceeds.
 *
 * When no more frames can be received from the peer (the peer closed the connection or it was terminated
 * abnormally), a handler that is still running is cancelled with a [CancellationException] after a short grace
 * period: a handler consuming [incoming] notices the closure and finishes on its own, while a handler that never
 * reads [incoming] and never sends frames (for example, it collects a shared flow waiting for data changes to
 * push) is terminated this way instead of running forever.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocket)
 */
public fun Route.webSocket(
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
): Route {
    return webSocketRaw(protocol, negotiateExtensions = true) {
        proceedWebSocket(handler)
    }
}

/**
 * Bind WebSocket at the current route + [path] optionally checking for the WebSocket [protocol] (ignored if `null`)
 * Requires [WebSockets] plugin to be installed.
 *
 * [DefaultWebSocketSession.incoming] will never contain any control frames and no fragmented frames could be found.
 * Default WebSocket implementation is handling ping/pongs, timeouts, close frames and reassembling fragmented frames.
 *
 * When a websocket session is created, a [handler] lambda will be called with WebSocket session instance on receiver.
 * Once [handler] function returns, the WebSocket termination sequence will be scheduled so you shouldn't use
 * [DefaultWebSocketSession] anymore. However, WebSocket could live for a while until close sequence completed or
 * a timeout exceeds.
 *
 * When no more frames can be received from the peer (the peer closed the connection or it was terminated
 * abnormally), a handler that is still running is cancelled with a [CancellationException] after a short grace
 * period: a handler consuming [incoming] notices the closure and finishes on its own, while a handler that never
 * reads [incoming] and never sends frames (for example, it collects a shared flow waiting for data changes to
 * push) is terminated this way instead of running forever.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.websocket.webSocket)
 */
public fun Route.webSocket(
    path: String,
    protocol: String? = null,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
): Route {
    return webSocketRaw(path, protocol, negotiateExtensions = true) {
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

/**
 * The time a WebSocket handler has to finish on its own after the close sequence of its session
 * has been performed. Only a handler that never consumed [DefaultWebSocketSession.incoming]
 * cannot notice the closure on its own, so it is cancelled after this period; a handler that
 * consumes it is left alone to drain the frames buffered by the session, however long that
 * takes.
 *
 * See KTOR-6686.
 */
private const val HANDLER_COMPLETION_GRACE_MILLIS = 1_000L

@OptIn(InternalAPI::class)
private suspend fun WebSocketServerSession.proceedWebSocket(handler: suspend DefaultWebSocketServerSession.() -> Unit) {
    val webSockets = application.plugin(WebSockets)

    val session = DefaultWebSocketSession(
        this,
        webSockets.pingIntervalMillis,
        webSockets.timeoutMillis,
        webSockets.channelsConfig,
    ).apply {
        val extensions = call.attributes[WebSockets.EXTENSIONS_KEY]
        start(extensions)
    }

    // KTOR-6686: run the user handler in a child job so it can be cancelled if it outlives its
    // connection. A handler that never reads [incoming] and never sends frames (e.g. it collects
    // a shared flow waiting for data changes to push) would otherwise keep running forever after
    // the peer closed the connection.
    //
    // The handler's outcome is delivered through [handlerResult] instead of letting the child
    // job fail, so a failing handler is still propagated to the caller as an exception and is
    // not reported as an unhandled coroutine failure. The success result is completed from the
    // coroutine body as soon as the handler returns, and not only from the job completion
    // handler: a job completes only after all its children, so a handler that started a
    // background coroutine of its own before returning would otherwise never complete its job
    // and never deliver its result. The completion handler below is retained as a fallback for
    // a job that is cancelled before the coroutine starts: a dispatcher that does not run the
    // dispatched handler within the grace period (e.g. a saturated engine event loop) never
    // enters the try/catch below, and only [Job.invokeOnCompletion] is guaranteed to run.
    val handlerResult = CompletableDeferred<Unit>()
    // The handler's session wrapper records whether the handler consumes a frame from [incoming]
    // or observes its closure (through any receive entry point, the select clauses, or the
    // iterator used by `for (frame in incoming)`). The
    // close watcher below cancels the handler after the grace period only when it never did: a
    // consumer observes the closed channel itself and terminates on its own, so cancelling it
    // would abandon frames that are already buffered but not yet processed.
    val incomingConsumption = IncomingConsumption()
    val trackedSession = TrackingIncomingSession(session, incomingConsumption)
    val handlerJob = launch {
        try {
            trackedSession.handleServerSession(call, handler)
            // The handler returned, so its outcome is known: deliver the success result before
            // waiting for the job to complete, then cancel the children it left behind so the
            // job can complete and the upgraded request reaches the session teardown, which
            // cancels them anyway (as it did before the handler ran in a child job). The close
            // watcher does not cancel them: a handler that consumed [incoming] is an active
            // consumer, and the children are not abandoned frames.
            handlerResult.complete(Unit)
            coroutineContext.cancelChildren()
        } catch (cancelled: CancellationException) {
            // Rethrow so the job completes as cancelled and the completion handler below decides
            // whether the cancellation ends the session normally or must reach the caller.
            throw cancelled
        } catch (cause: Throwable) {
            // A failing handler is not a failure of the routing coroutine: deliver the exception
            // through [handlerResult] and let the job finish normally instead of failing its
            // parent, which would cancel the whole upgraded request outside of [handlerResult].
            handlerResult.completeExceptionally(cause)
        }
    }

    handlerJob.invokeOnCompletion { cause ->
        when {
            cause == null -> handlerResult.complete(Unit)

            // The watcher cancels the handler job only after the close sequence of the session has
            // been performed, so that cancellation (as well as a normal handler completion) is a
            // normal session end. While the session is still open, a CancellationException can
            // only come from the handler itself (e.g. an uncaught `withTimeout`) or from
            // application shutdown. It must not be swallowed: deliver it to the caller, which
            // cancels the upgraded request and the socket as it did before the handler ran in a
            // child job.
            cause is CancellationException && session.closeReason.isCompleted -> handlerResult.complete(Unit)

            else -> handlerResult.completeExceptionally(cause)
        }
    }

    session.closeReason.invokeOnCompletion { cause ->
        // The close sequence has been performed (the peer closed the connection, the connection
        // was terminated abnormally, or the ping timeout expired), so the session is over. Only a
        // handler that never consumed [incoming] cannot notice the closure on its own (it neither
        // reads frames nor observes the channel closing through any of its entry points), so give
        // it the grace period to finish
        // and cancel it afterwards if it is still running. A handler that consumes [incoming] is
        // not cancelled: it observes the closed channel as soon as it processed the frames that
        // are already buffered, even when that takes longer than the grace period. The watcher is
        // launched on the plugin scope because the session processors are being torn down at this
        // point.
        webSockets.launch {
            delay(HANDLER_COMPLETION_GRACE_MILLIS)
            if (!incomingConsumption.consumed) {
                handlerJob.cancel(CancellationException("WebSocket session is closed", cause))
            }
        }
    }

    handlerResult.await()
    session.joinSession()
}

private suspend fun CoroutineScope.joinSession() {
    coroutineContext[Job]!!.join()
}

/**
 * Holds whether the handler consumed a frame from `incoming` or observed the channel closing.
 * Written by the tracking receive channel below, read by the close watcher in
 * [proceedWebSocket]; [Volatile] is sufficient because the flag is only ever flipped from
 * `false` to `true`.
 */
private class IncomingConsumption {
    @Volatile
    var consumed: Boolean = false
}

/**
 * Delegates everything to [delegate] but replaces [incoming] with a channel that reports frame
 * consumption and closure observation to [consumption], so the close watcher in
 * [proceedWebSocket] can tell a handler that reads frames (which ends on its own when the closed
 * channel is drained) from a handler that never does (which is cancelled after the grace period).
 */
private class TrackingIncomingSession(
    delegate: DefaultWebSocketSession,
    consumption: IncomingConsumption
) : DefaultWebSocketSession by delegate {
    override val incoming: ReceiveChannel<Frame> = TrackingIncomingChannel(delegate.incoming, consumption)
}

private class TrackingIncomingChannel(
    private val delegate: ReceiveChannel<Frame>,
    private val consumption: IncomingConsumption
) : ReceiveChannel<Frame> by delegate {
    override suspend fun receive(): Frame {
        val frame = delegate.receive()
        consumption.consumed = true
        return frame
    }

    override suspend fun receiveCatching(): ChannelResult<Frame> {
        val result = delegate.receiveCatching()
        // A successful result consumed a frame; a closed result is the handler observing the
        // session end itself, which means it will terminate on its own either way.
        consumption.consumed = true
        return result
    }

    override fun tryReceive(): ChannelResult<Frame> {
        val result = delegate.tryReceive()
        // A frame or the closed-channel outcome is an observation that ends the handler on its
        // own, but an empty result is not: a handler that only ever sees it has not noticed
        // anything and must stay a never-read consumer for the close watcher.
        if (result.isSuccess || result.isClosed) consumption.consumed = true
        return result
    }

    // A handler that consumes `incoming` through `select` does so via these clauses: evaluating
    // `incoming.onReceive` inside a select builder is the moment the handler declares itself an
    // active consumer, so the access is what marks the consumption. The clauses themselves are
    // forwarded unchanged: kotlinx.coroutines seals `SelectClause1` and keeps its construction
    // internal, so clauses cannot be wrapped from outside the library.
    override val onReceive: SelectClause1<Frame>
        get() {
            consumption.consumed = true
            return delegate.onReceive
        }

    override val onReceiveCatching: SelectClause1<ChannelResult<Frame>>
        get() {
            consumption.consumed = true
            return delegate.onReceiveCatching
        }

    override fun iterator(): ChannelIterator<Frame> = TrackingIncomingIterator(delegate.iterator(), consumption)
}

/**
 * Marks the flag on [hasNext] instead of [next]: [hasNext] is where an outcome becomes available
 * to the consumer, and a `for` loop over `incoming` uses this iterator under the hood. Both
 * outcomes count: `true` delivered a frame, `false` means the consumer observed the channel
 * closing and will exit the loop to finish on its own.
 */
private class TrackingIncomingIterator(
    private val delegate: ChannelIterator<Frame>,
    private val consumption: IncomingConsumption
) : ChannelIterator<Frame> {
    override suspend fun hasNext(): Boolean {
        val hasNext = delegate.hasNext()
        consumption.consumed = true
        return hasNext
    }

    override fun next(): Frame = delegate.next()
}

private suspend fun DefaultWebSocketSession.handleServerSession(
    call: ApplicationCall,
    handler: suspend DefaultWebSocketServerSession.() -> Unit
) {
    try {
        LOGGER.trace { "Starting websocket session for ${call.request.uri}" }
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
    override suspend fun evaluate(context: RoutingResolveContext, segmentIndex: Int): RouteSelectorEvaluation {
        val protocols = context.call.request.headers[HttpHeaders.SecWebSocketProtocol]
        if (protocols == null) {
            LOGGER.trace("Skipping WebSocket plugin because no Sec-WebSocket-Protocol header provided.")
            return RouteSelectorEvaluation.FailedParameter
        }

        if (requiredProtocol in parseHeaderValue(protocols).map { it.value }) {
            return RouteSelectorEvaluation.Constant
        }

        LOGGER.trace {
            "Skipping WebSocket plugin because no Sec-WebSocket-Protocol " +
                "header $protocols is not matching $requiredProtocol."
        }
        return RouteSelectorEvaluation.FailedParameter
    }
}
