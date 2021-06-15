/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import kotlinx.atomicfu.*

internal class AuthTokenHolder<T>(
    private val loadTokens: suspend () -> T?
) {
    private val initialized = atomic(false)

    private val cachedBearerTokens: AtomicRef<T?> = atomic(null)

    internal fun clearToken() {
        cachedBearerTokens.value = null
    }

    internal suspend fun loadToken(): T? {
        if (initialized.compareAndSet(false, true)) {
            val token = loadTokens()
            cachedBearerTokens.value = token
            return token
        }

        return cachedBearerTokens.value
    }

    internal suspend fun setToken(block: suspend () -> T?): T? {
        val token = block()
        cachedBearerTokens.value = token
        return token
    }
}
