/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Non-JVM implementation of RoutingResolveState pool.
// ABOUTME: Creates new instances on each call since ThreadLocal is JVM-specific.

package io.ktor.server.routing

internal actual fun acquireRoutingResolveState(): RoutingResolveState {
    return RoutingResolveState()
}
