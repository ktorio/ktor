package io.ktor.http.cio.websocket

import io.ktor.util.cio.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.io.pool.*
import java.nio.*
import java.time.*
import kotlin.coroutines.*

/**
 * Ping interval or `null` to disable pinger. Please note that pongs will be handled despite of this setting.
 */
inline var DefaultWebSocketServerSession.pingInterval: Duration?
    get() = pingIntervalMillis.takeIf { it >= 0L }?.let { Duration.ofMillis(it) }
    set(newDuration: Duration?) {
        pingIntervalMillis = newDuration?.toMillis() ?: -1L
    }

/**
 * A timeout to wait for pong reply to ping otherwise the session will be terminated immediately.
 * It doesn't have any effect if [pingInterval] is `null` (pinger is disabled).
 */
inline var DefaultWebSocketServerSession.timeout: Duration
    get() = Duration.ofMillis(timeoutMillis)
    set(newDuration) {
        timeoutMillis = newDuration.toMillis()
    }

/**
 * Launch pinger coroutine on [coroutineContext] websocket for [session] that is sending ping every specified [period],
 * waiting for and verifying client's pong frames. It is also handling [timeout] and sending timeout close frame
 * to the dedicated [out] channel in case of failure
 */
@Deprecated(
    "Use pinger on CoroutineScope",
    ReplaceWith("session.pinger(session.outgoing, period, timeout, out, pool)"),
    level = DeprecationLevel.ERROR
)
@Suppress("UNUSED_PARAMETER")
fun pinger(
    session: WebSocketSession,
    period: Duration,
    timeout: Duration,
    out: SendChannel<Frame>,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Pong> = session.pinger(session.outgoing, period, timeout, pool)

/**
 * Launch pinger coroutine on [CoroutineScope] that is sending ping every specified [period] to [outgoing] channel,
 * waiting for and verifying client's pong frames. It is also handling [timeout] and sending timeout close frame
 */
@UseExperimental(ExperimentalCoroutinesApi::class, ObsoleteCoroutinesApi::class)
fun CoroutineScope.pinger(
    outgoing: SendChannel<Frame>,
    period: Duration,
    timeout: Duration,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Pong> = pinger(outgoing, period.toMillis(), timeout.toMillis(), pool)
