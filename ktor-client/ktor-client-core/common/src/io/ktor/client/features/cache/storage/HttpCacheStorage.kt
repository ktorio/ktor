package io.ktor.client.features.cache.storage

import io.ktor.client.features.cache.HttpCacheEntry
import io.ktor.client.response.*
import io.ktor.http.*
import io.ktor.util.*

/**
 * Cache storage interface.
 */
@KtorExperimentalAPI
abstract class HttpCacheStorage {

    /**
     * Store [value] in cache storage for [url] key.
     */
    abstract fun store(url: Url, value: HttpCacheEntry)

    /**
     * Find valid entry in cache storage with additional [varyKeys].
     */
    abstract fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry?

    /**
     * Find all matched [HttpCacheEntry] for [url].
     */
    abstract fun findByUrl(url: Url): Set<HttpCacheEntry>

    companion object {
        /**
         * Default unlimited cache storage.
         */
        val Unlimited: () -> HttpCacheStorage = { UnlimitedCacheStorage() }

        /**
         * Disabled cache always empty and store nothing.
         */
        val Disabled: HttpCacheStorage = DisabledCacheStorage
    }
}

internal suspend fun HttpCacheStorage.store(url: Url, value: HttpResponse): HttpCacheEntry {
    val result = HttpCacheEntry(value)
    store(url, result)
    return result
}
