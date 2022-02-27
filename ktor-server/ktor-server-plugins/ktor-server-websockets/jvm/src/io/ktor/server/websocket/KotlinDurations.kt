/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("FunctionName")

package io.ktor.server.websocket

import io.ktor.util.cio.*
import io.ktor.utils.io.pool.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import java.nio.*
import kotlin.time.*

/**
 * WebSockets support plugin. It is required to be installed first before binding any websocket endpoints
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
 *
 * @param pingInterval duration between pings or `null` to disable pings.
 * @param timeout write/ping timeout after that a connection will be closed.
 * @param maxFrameSize maximum frame that could be received or sent.
 * @param masking whether masking need to be enabled (useful for security).
 */
public fun WebSockets(
    pingInterval: Duration?,
    timeout: Duration,
    maxFrameSize: Long,
    masking: Boolean
): WebSockets = WebSockets(
    pingInterval?.inWholeMilliseconds ?: 0L,
    timeout.inWholeMilliseconds,
    maxFrameSize,
    masking
)

/**
 * Launch pinger coroutine on [CoroutineScope] that is sending ping every specified [period] to [outgoing] channel,
 * waiting for and verifying client's pong frames. It is also handling [timeout] and sending timeout close frame
 */
public fun CoroutineScope.pinger(
    outgoing: SendChannel<Frame>,
    period: Duration,
    timeout: Duration,
    pool: ObjectPool<ByteBuffer> = KtorDefaultPool
): SendChannel<Frame.Pong> = pinger(outgoing, period.inWholeMilliseconds, timeout.inWholeMilliseconds, pool)
