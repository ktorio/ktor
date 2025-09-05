/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Service locator is not supported on non-jvm platforms.
 */
internal actual fun loadMapExtensions(): List<DependencyMapExtension> =
    emptyList()

/**
 * Blocking is unavailable for other platforms, so we instead attempt to get the completed value.
 *
 * If the dependency is not ready, [MissingDependencyException] is thrown.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.plugins.di.getBlocking)
 */
@OptIn(ExperimentalCoroutinesApi::class)
public actual fun <T> DependencyResolver.getBlocking(key: DependencyKey): T {
    val deferred = getDeferred<T>(key)
    return if (deferred.isCompleted) {
        deferred.getCompleted()
    } else {
        throw MissingDependencyException(key)
    }
}
