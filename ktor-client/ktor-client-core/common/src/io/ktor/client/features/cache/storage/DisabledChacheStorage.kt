package io.ktor.client.features.cache.storage

import io.ktor.client.features.cache.*
import io.ktor.http.*

internal object DisabledCacheStorage : HttpCacheStorage() {
    override fun store(url: Url, value: HttpCacheEntry) {}

    override fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry? = null

    override fun findByUrl(url: Url): Set<HttpCacheEntry> = emptySet()
}
