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
        val deferred = loadTokensDeferred.getAndUpdate { it ?: CompletableDeferred() }
        return if (deferred == null) {
            val newTokens = loadTokens()
            loadTokensDeferred.value!!.complete(newTokens)
            newTokens
        } else {
            deferred.await()
        }
    }

    internal suspend fun setToken(block: suspend () -> T?): T? {
        val deferred = refreshTokensDeferred.getAndUpdate { it ?: CompletableDeferred() }
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
