/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Thread-local pool for RoutingResolveState instances.
// ABOUTME: Uses expect/actual to provide ThreadLocal on JVM and simple allocation on other platforms.

package io.ktor.server.routing

/**
 * Acquires a [RoutingResolveState] instance from the thread-local pool.
 * On JVM, this reuses instances per thread. On other platforms, creates new instances.
 * The returned state is already reset and ready for use.
 */
internal expect fun acquireRoutingResolveState(): RoutingResolveState
