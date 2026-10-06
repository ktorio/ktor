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
 * Builds a function resolving the [EventExecutor] a call's coroutine should be dispatched onto and
 * resumed on, for a given [ChannelHandlerContext].
 *
 * When [shareWorkGroup] is `true`, [callEventGroup] is the same group that drives the channel's own I/O.
 * [EventExecutorGroup.next] could still pick a different thread within that group than the channel's own
 * event loop, so the resolver is pinned directly to [ChannelHandlerContext.executor] instead: this
 * guarantees the initial, un-suspended dispatch and any post-suspension resumption land on the exact same
 * thread.
 *
 * Otherwise, the resolver selects an [EventExecutor] from [callEventGroup] once per channel and caches it
 * as a channel attribute, so all calls on a given connection (HTTP/1) or stream (HTTP/2) are dispatched
 * onto a single thread for the lifetime of the channel. This preserves thread affinity across coroutine
 * suspensions, allowing user code to observe the same thread before and after `withContext` / other
 * suspension points.
 */
internal fun callExecutorResolver(
    callEventGroup: EventExecutorGroup,
    shareWorkGroup: Boolean
): (ChannelHandlerContext) -> EventExecutor {
    if (shareWorkGroup) return ChannelHandlerContext::executor
    return { context ->
        val attr = context.channel().attr(PinnedCallExecutorKey)
        val existing = attr.get()
        if (existing != null) {
            existing
        } else {
            val picked = callEventGroup.next()
            if (attr.compareAndSet(null, picked)) {
                picked
            } else {
                attr.get()
            }
        }
    }
}
