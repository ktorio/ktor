/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import kotlinx.atomicfu.*
import kotlinx.coroutines.*

internal class AuthTokenHolder<T>(
    private val loadTokens: suspend () -> T?
) {
    private val refreshTokensDeferred = atomic<CompletableDeferred<T?>?>(null)
    private val loadTokensDeferred = atomic<CompletableDeferred<T?>?>(null)

    internal fun clearToken() {
        loadTokensDeferred.value = null
        refreshTokensDeferred.value = null
    }

    internal suspend fun loadToken(): T? {
        var deferred: CompletableDeferred<T?>?
        while (true) {
            deferred = loadTokensDeferred.value
            val newValue = deferred ?: CompletableDeferred()
            if (loadTokensDeferred.compareAndSet(deferred, newValue)) break
        }

        if (deferred != null) {
            return deferred.await()
        }

        val newTokens = loadTokens()
        loadTokensDeferred.value!!.complete(newTokens)
        return newTokens
    }

    internal suspend fun setToken(block: suspend () -> T?): T? {
        var deferred: CompletableDeferred<T?>?
        while (true) {
            deferred = refreshTokensDeferred.value
            val newValue = deferred ?: CompletableDeferred()
            if (refreshTokensDeferred.compareAndSet(deferred, newValue)) break
        }

        val newToken = if (deferred == null) {
            val newTokens = block()
            refreshTokensDeferred.value!!.complete(newTokens)
            refreshTokensDeferred.value = null
            newTokens
        } else {
            deferred.await()
        }
        loadTokensDeferred.value = CompletableDeferred(newToken)
        return newToken
    }
}
