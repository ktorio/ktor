/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.application

import io.ktor.utils.io.InternalAPI
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.channels.Channel
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Counts the modules that are running during concurrent startup and are not suspended waiting on a dependency.
 */
@InternalAPI
public class ParallelModuleTracker : AbstractCoroutineContextElement(Key) {
    public companion object Key : CoroutineContext.Key<ParallelModuleTracker>

    private val active = atomic(0)
    private val idle = Channel<Unit>(Channel.CONFLATED)

    public fun increment() {
        active.incrementAndGet()
    }

    public fun decrement() {
        if (active.decrementAndGet() == 0) idle.trySend(Unit)
    }

    /** Makes [awaitIdle] fail with the given [cause]. */
    public fun abort(cause: Throwable) {
        idle.close(cause)
    }

    /** Suspends until no module is actively running. */
    public suspend fun awaitIdle() {
        while (active.value != 0) idle.receive()
    }
}
