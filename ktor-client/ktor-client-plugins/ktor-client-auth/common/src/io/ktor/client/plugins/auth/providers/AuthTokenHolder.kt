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
        var newDeferred: CompletableDeferred<T?>? = null
        while (true) {
            deferred = loadTokensDeferred.value
            val newValue = deferred ?: CompletableDeferred()
            if (loadTokensDeferred.compareAndSet(deferred, newValue))
                newDeferred = newValue
                break
        }

        // if there's already a pending loadTokens(), just wait for it to complete
        if (deferred != null) {
            return deferred.await()
        }

        // load the tokens, but keep in mind this is a suspending function
        val newTokens = loadTokens()

        // [loadTokensDeferred.value] could be null by now (if clearToken() was called while
        // suspended), which is why we are using [newDeferred] to complete the suspending callback.
        // [newDeferred] can't be null as it must have been set to exit the while loop earlier on.
        newDeferred!!.complete(newTokens)

        return newTokens
    }

    internal suspend fun setToken(block: suspend () -> T?): T? {
        var deferred: CompletableDeferred<T?>?
        var newDeferred: CompletableDeferred<T?>? = null
        while (true) {
            deferred = refreshTokensDeferred.value
            val newValue = deferred ?: CompletableDeferred()
            if (refreshTokensDeferred.compareAndSet(deferred, newValue))
                newDeferred = newValue
                break
        }

        val newToken = if (deferred == null) {
            // set the tokens, which is a suspending call
            val newTokens = block()

            // [refreshTokensDeferred.value] could be null by now (if clearToken() was called while
            // suspended), which is why we are using [newDeferred] to complete the suspending callback.
            // [newDeferred] can't be null as it must have been set to exit the while loop earlier on.
            newDeferred!!.complete(newTokens)
            refreshTokensDeferred.value = null
            newTokens
        } else {
            deferred.await()
        }
        loadTokensDeferred.value = CompletableDeferred(newToken)
        return newToken
    }
}
