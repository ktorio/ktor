/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JVM implementation of RoutingResolveState pool using ThreadLocal.
// ABOUTME: Enables zero-allocation routing resolution after thread warmup.

package io.ktor.server.routing

private val statePool = ThreadLocal<RoutingResolveState>()

internal actual fun acquireRoutingResolveState(): RoutingResolveState {
    val cached = statePool.get()
    if (cached != null) {
        cached.reset()
        return cached
    }
    return RoutingResolveState().also { statePool.set(it) }
}
