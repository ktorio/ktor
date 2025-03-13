/*
 * Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.util.collections.*
import io.ktor.utils.io.*
import io.ktor.utils.io.locks.*
import kotlinx.atomicfu.*
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.*
import kotlinx.coroutines.CancellationException

/**
 * Thread-safe
 */
internal class AuthTokenHolder<T>(private val loadTokens: suspend () -> T?) {
    private val ref = atomic<T?>(null)

    @OptIn(InternalAPI::class) private val lock = SynchronizedObject()

    private val loadJobs = ConcurrentSet<Deferred<T?>>()
    private val setJobs = ConcurrentSet<Deferred<T?>>()

    private object JobCancelledException : CancellationException("Job cancelled")

    /**
     * Returns a cached reference if any. Otherwise, computes a value using [loadTokens] and caches it.
     * If there are loadToken coroutines in-progress, the first resumed one wins and the other ones are cancelled.
     */
    @OptIn(InternalAPI::class)
    internal suspend fun loadToken(): T? {
        if (ref.value != null) return ref.value

        return coroutineScope {
            val deferred = async {
                loadTokens()
            }

            loadJobs.add(deferred)

            return@coroutineScope try {
                val value = deferred.await()

                synchronized(lock) {
                    if (deferred in loadJobs) {
                        ref.value = value

                        for (def in loadJobs) {
                            def.cancel(JobCancelledException)
                        }

                        loadJobs.clear()
                        ref.value
                    }

                    ref.value
                }
            } catch (_: JobCancelledException) {
                ref.value
            }
        }
    }

    /**
     * Replaces the current cached value with one computed with [block].
     * If there are loadToken and/or setToken coroutines in-progress,
     * the first resumed setToken coroutine wins and the other ones are cancelled.
     */
    @OptIn(InternalAPI::class)
    internal suspend fun setToken(block: suspend () -> T?): T? = coroutineScope {
        val deferred = async {
            block()
        }

        setJobs.add(deferred)

        return@coroutineScope try {
            val value = deferred.await()

            synchronized(lock) {
                if (deferred in setJobs) {
                    ref.value = value

                    for (def in loadJobs) {
                        def.cancel(JobCancelledException)
                    }

                    for (def in setJobs) {
                        def.cancel(JobCancelledException)
                    }

                    loadJobs.clear()
                    setJobs.clear()
                }

                ref.value
            }
        } catch (_: JobCancelledException) {
            ref.value
        }
    }

    /**
     * Resets the cached value.
     * Cancels all in-progress loadToken and setToken coroutines.
     */
    @OptIn(InternalAPI::class)
    internal fun clearToken() {
        synchronized(lock) {
            ref.value = null

            for (def in loadJobs) {
                def.cancel(JobCancelledException)
            }

            for (def in setJobs) {
                def.cancel(JobCancelledException)
            }

            loadJobs.clear()
            setJobs.clear()
        }
    }
}
