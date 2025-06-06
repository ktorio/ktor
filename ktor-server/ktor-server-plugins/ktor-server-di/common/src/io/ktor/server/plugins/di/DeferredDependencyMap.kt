/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import kotlinx.coroutines.*

internal typealias DependencyInitializerMap =
    MutableMap<DependencyKey, DependencyInitializer>

internal fun DependencyInitializerMap.isProvided(key: DependencyKey): Boolean =
    get(key)?.takeIf { it !is DependencyInitializer.Missing } != null

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> Deferred<T>.tryGetCompleted(): T? =
    if (isCompleted) {
        val cause = getCompletionExceptionOrNull()
        if (cause != null) throw cause
        getCompleted()
    } else {
        null
    }

internal fun DependencyInitializerMap.findOrigin(key: DependencyKey): DependencyKey =
    get(key)?.originKey ?: key

@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T, U : T> CompletableDeferred<T>.completeWith(other: Deferred<U>) {
    other.invokeOnCompletion { cause ->
        if (cause != null) {
            completeExceptionally(cause)
        } else {
            complete(other.getCompleted())
        }
    }
}
