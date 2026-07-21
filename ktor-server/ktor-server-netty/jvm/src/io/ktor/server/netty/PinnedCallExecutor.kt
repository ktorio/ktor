/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.util.AttributeKey
import io.netty.util.concurrent.EventExecutor
import io.netty.util.concurrent.EventExecutorGroup

private val PinnedCallExecutorKey: AttributeKey<EventExecutor> =
    AttributeKey.valueOf("ktor.netty.pinnedCallExecutor")

/**
 * Returns the [EventExecutor] from [callEventGroup] pinned to the given Netty channel.
 *
 * The executor is selected once per channel and cached as a channel attribute, so all calls on a given
 * connection (HTTP/1) or stream (HTTP/2) are dispatched onto a single thread for the lifetime of the
 * channel. This preserves thread affinity across coroutine suspensions, allowing user code to observe
 * the same thread before and after `withContext` / other suspension points.
 */
internal fun pinnedCallExecutor(
    context: ChannelHandlerContext,
    callEventGroup: EventExecutorGroup
): EventExecutor {
    val attr = context.channel().attr(PinnedCallExecutorKey)
    val existing = attr.get()
    if (existing != null) return existing
    val picked = callEventGroup.next()
    return if (attr.compareAndSet(null, picked)) picked else attr.get()
}
