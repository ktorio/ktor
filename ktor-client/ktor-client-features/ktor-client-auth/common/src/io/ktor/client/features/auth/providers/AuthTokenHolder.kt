/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.features.auth.providers

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.*

internal class AuthTokenHolder<T>(
    private val loadTokens: suspend () -> T?
) {

    private val lock = Mutex()

    private val cachedBearerTokens: AtomicRef<CompletableDeferred<T?>> = atomic(
        CompletableDeferred<T?>().apply {
            complete(null)
        }
    )

    internal suspend fun clearToken() {
        lock.withLock {
            cachedBearerTokens.value = CompletableDeferred<T?>().apply { complete(null) }
        }
    }

    internal suspend fun loadToken(): T? = lock.withLock {
        val cachedToken = cachedBearerTokens.value.await()
        if (cachedToken != null) return cachedToken

        return setToken(loadTokens)
    }

    internal suspend fun setToken(block: suspend () -> T?): T? {
        val old = cachedBearerTokens.value
        if (!old.isCompleted) {
            return old.await()
        }

        val deferred = CompletableDeferred<T?>()
        if (!cachedBearerTokens.compareAndSet(old, deferred)) {
            return cachedBearerTokens.value.await()
        }

        try {
            val token = block()
            deferred.complete(token)
            return token
        } catch (cause: Throwable) {
            deferred.completeExceptionally(cause)
            throw cause
        }
    }
}
