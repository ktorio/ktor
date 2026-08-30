/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.netty.channel.*
import kotlinx.atomicfu.*

internal class NettyHttpHandlerState(private val runningLimit: Int) {

    internal val activeRequests: AtomicLong = atomic(0L)
    internal val isCurrentRequestFullyRead: AtomicBoolean = atomic(false)
    internal val isChannelReadCompleted: AtomicBoolean = atomic(false)
    internal val skippedRead: AtomicBoolean = atomic(false)

    internal fun onLastResponseMessage(context: ChannelHandlerContext) {
        activeRequests.decrementAndGet()

        if (skippedRead.compareAndSet(expect = false, update = true) && activeRequests.value < runningLimit) {
            context.read()
        }
    }
}
