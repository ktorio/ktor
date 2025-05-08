/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.auth.providers

import io.ktor.client.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * Interface for auth token storage.
 * Used by auth providers to load, store, and manage authentication tokens.
 *
 * You can provide your own implementation to customize token caching and management behavior.
 *
 * @param T the type of token to store
 */
public interface TokenStorage<T> {
    /**
     * Loads a token from storage.
     * May return cached or freshly loaded token based on implementation.
     *
     * @return the token or null if not available
     */
    public suspend fun loadToken(): T?

    /**
     * Updates the token with a new value from [block].
     *
     * @param block function that returns a new token value
     * @return the updated token or null if not available
     */
    public suspend fun updateToken(block: suspend () -> T?): T?

    /**
     * Clears the currently stored token.
     */
    public suspend fun clearToken()
}

/**
 * Default token storage interface that provides access to the raw token value.
 * This interface is useful for testing purposes.
 *
 * @param T the type of token to store
 */
public interface DefaultTokenStorage<T> : TokenStorage<T> {
    /**
     * Returns the current token value without loading it.
     * Mostly used for testing purposes.
     *
     * @return the current token or null if not available
     */
    public fun getCurrentToken(): T?
}

/**
 * Factory object for creating token storage implementations.
 */
public object TokenStorageFactory {
    /**
     * Key used to store token storage in the HttpClient attributes.
     */
    public val TokenStorageAttributeKey: AttributeKey<MutableMap<String, TokenStorage<*>>> =
        AttributeKey("TokenStorageKey")

    /**
     * Creates a caching token storage implementation that stores tokens in memory.
     *
     * @param T the type of token to store
     * @param loadTokens provider function for loading tokens
     * @return a caching token storage implementation
     */
    public fun <T> withCache(
        loadTokens: suspend () -> T?
    ): DefaultTokenStorage<T> = CachingTokenStorage(loadTokens)

    /**
     * Creates a non-caching token storage implementation that always loads tokens fresh.
     *
     * @param T the type of token to store
     * @param loadTokens provider function for loading tokens
     * @return a non-caching token storage implementation
     */
    public fun <T> nonCaching(
        loadTokens: suspend () -> T?
    ): TokenStorage<T> = NonCachingTokenStorage(loadTokens)
}

/**
 * Stores a token storage instance in the HttpClient attributes with the given key.
 *
 * @param T the type of token
 * @param key the key to store the token storage under
 * @param storage the token storage to store
 */
public fun <T> HttpClient.registerTokenStorage(key: String, storage: TokenStorage<T>) {
    val storages = attributes.computeIfAbsent(TokenStorageFactory.TokenStorageAttributeKey) { mutableMapOf() }
    storages[key] = storage
}

/**
 * Gets a token storage instance from the HttpClient attributes with the given key.
 *
 * @param T the type of token
 * @param key the key the token storage is stored under
 * @return the token storage or null if not found
 */
@Suppress("UNCHECKED_CAST")
public fun <T> HttpClient.getTokenStorage(key: String): TokenStorage<T>? =
    attributes.getOrNull(TokenStorageFactory.TokenStorageAttributeKey)?.get(key) as? TokenStorage<T>

/**
 * Standard implementation of [TokenStorage] that caches tokens in memory.
 *
 * @param T the type of token to store
 * @param loadTokens provider function for loading tokens
 */
public class CachingTokenStorage<T>(
    private val loadTokens: suspend () -> T?
) : DefaultTokenStorage<T> {

    @Volatile private var value: T? = null

    @Volatile private var isLoadRequest = false
    private val mutex = Mutex()

    override fun getCurrentToken(): T? = value

    /**
     * Returns a cached value if any. Otherwise, computes a value using [loadTokens] and caches it.
     * Only one [loadToken] call can be executed at a time. The other calls are suspended and have no effect on the cached value.
     */
    override suspend fun loadToken(): T? {
        if (value != null) return value // Hot path
        val prevValue = value

        return if (coroutineContext[SetTokenContext] != null) { // Already locked by updateToken
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
     * Only one [loadToken] or [updateToken] call can be executed at a time,
     * although the resumed [updateToken] call recomputes the value cached by [loadToken].
     */
    override suspend fun updateToken(block: suspend () -> T?): T? {
        val prevValue = value
        val lockedByLoad = isLoadRequest

        return mutex.withLock {
            if (prevValue == value || lockedByLoad) { // Raced first
                val newValue = withContext(coroutineContext + setTokenMarker) {
                    block()
                }

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
    override suspend fun clearToken() {
        mutex.withLock {
            value = null
        }
    }
}

/**
 * Implementation of [TokenStorage] that doesn't cache tokens.
 * Every call to [loadToken] will invoke the provider function to get a fresh token.
 *
 * @param T the type of token to store
 * @param loadTokens provider function for loading tokens
 */
public class NonCachingTokenStorage<T>(
    private val loadTokens: suspend () -> T?
) : TokenStorage<T> {
    // This token is used only during the refresh process
    @Volatile private var currentRefreshingToken: T? = null
    private val mutex = Mutex()

    override suspend fun loadToken(): T? {
        mutex.withLock {
            // If we have a token from an ongoing refresh operation, use that
            if (currentRefreshingToken != null) {
                return currentRefreshingToken
            }
        }

        // Otherwise, load a fresh token
        return loadTokens()
    }

    override suspend fun updateToken(block: suspend () -> T?): T? {
        return mutex.withLock {
            val newToken = block()
            currentRefreshingToken = newToken
            newToken
        }
    }

    override suspend fun clearToken() {
        mutex.withLock {
            currentRefreshingToken = null
        }
    }
}
