/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.plugins.di

import io.ktor.util.reflect.loadServices
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.runBlocking

@OptIn(InternalAPI::class)
internal actual fun loadMapExtensions(): List<DependencyMapExtension> =
    loadServices<DependencyMapExtension>()

public actual fun <T> DependencyResolver.getBlocking(key: DependencyKey): T {
    val deferred = getDeferred<T>(key)
    return deferred.tryGetCompleted()
        ?: runBlocking { deferred.await() }
}
