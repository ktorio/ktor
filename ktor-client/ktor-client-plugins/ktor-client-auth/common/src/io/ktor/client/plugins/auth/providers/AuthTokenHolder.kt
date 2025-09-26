/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

internal class AuthTokenHolder<T>(private val loadTokens: suspend () -> T?) {

    @Volatile private var value: T? = null

    @Volatile private var isLoadRequest = false

    private val mutex = Mutex()

    /**
     * Exist only for testing
     */
    internal fun get(): T? = value

    /**
     * Returns a cached value if any. Otherwise, computes a value using [loadTokens] and caches it.
     * Only one [loadToken] call can be executed at a time. The other calls are suspended and have no effect on the cached value.
     */
    internal suspend fun loadToken(): T? {
        if (value != null) return value // Hot path
        val prevValue = value

        return if (coroutineContext[SetTokenContext] != null) { // Already locked by setToken
            value = loadTokens()
            value
        } else {
            mutex.withLock {
                isLoadRequest = true
                try {
                    if (prevValue == value) { // Raced first
                        value = loadTokens()
                    }
                } finally {
                    isLoadRequest = false
                }

                value
            }
        }
    }

    private class SetTokenContext : CoroutineContext.Element {
        override val key: CoroutineContext.Key<*>
            get() = SetTokenContext

        companion object : CoroutineContext.Key<SetTokenContext>
    }

    private val setTokenMarker = SetTokenContext()

    /**
     * Replaces the current cached value with one computed with [block].
     * Only one [loadToken] or [setToken] call can be executed at a time,
     * although the resumed [setToken] call recomputes the value cached by [loadToken].
     */
    internal suspend fun setToken(block: suspend () -> T?): T? {
        val prevValue = value
        val lockedByLoad = isLoadRequest

        return mutex.withLock {
            if (prevValue == value || lockedByLoad) { // Raced first
                value = withContext(coroutineContext + setTokenMarker) {
                    block()
                }
            }

            value
        }
    }

    /**
     * Resets the cached value.
     */
    @OptIn(DelicateCoroutinesApi::class)
    internal fun clearToken(coroutineScope: CoroutineScope = GlobalScope) {
        if (mutex.tryLock()) {
            value = null
            mutex.unlock()
        } else {
            coroutineScope.launch {
                mutex.withLock {
                    value = null
                }
            }
        }
    }
}
