/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile

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

        return mutex.withLock {
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

    /**
     * Replaces the current cached value with one computed with [block].
     * Only one [loadToken] or [setToken] call can be executed at a time,
     * although the resumed setToken call recomputes the value cached by [loadToken].
     * DO NOT call [loadToken] inside the [block] because this will lead to a deadlock.
     */
    internal suspend fun setToken(block: suspend () -> T?): T? {
        val prevValue = value
        val lockedByLoad = isLoadRequest

        return mutex.withLock {
            if (prevValue == value || lockedByLoad) { // Raced first
                val newValue = block()

                if (newValue != null) {
                    value = newValue
                }
            }

            value
        }
    }

    /**
     * Resets the cached value.
     */
    @OptIn(DelicateCoroutinesApi::class)
    internal fun clearToken() {
        if (mutex.tryLock()) {
            value = null
            mutex.unlock()
        } else {
            GlobalScope.launch {
                mutex.withLock {
                    value = null
                }
            }
        }
    }
}
