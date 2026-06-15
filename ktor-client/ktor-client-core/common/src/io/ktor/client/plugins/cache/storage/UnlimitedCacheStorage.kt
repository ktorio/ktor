/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.plugins.cache.storage

import io.ktor.client.plugins.cache.*
import io.ktor.http.*
import io.ktor.util.collections.*

@Suppress("DEPRECATION_ERROR")
internal class UnlimitedCacheStorage : HttpCacheStorage() {
    private val store = ConcurrentMap<Url, MutableSet<HttpCacheEntry>>()

    override fun store(url: Url, value: HttpCacheEntry) {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        if (!data.add(value)) {
            data.remove(value)
            data.add(value)
        }
    }

    override fun find(url: Url, varyKeys: Map<String, String>): HttpCacheEntry? {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override fun findByUrl(url: Url): Set<HttpCacheEntry> = store[url] ?: emptySet()

    internal fun remove(url: Url, varyKeys: Map<String, String>) =
        store[url]?.remove(varyKeys) { it.varyKeys }
}

private inline fun <E> MutableSet<E>.remove(
    varyKeys: Map<String, String>,
    crossinline entryVaryKeys: (E) -> Map<String, String>
) {
    val entriesToRemove = filter { entry ->
        val keys = entryVaryKeys(entry)
        varyKeys.size == keys.size && varyKeys.all { (key, value) -> keys[key] == value }
    }
    entriesToRemove.forEach { remove(it) }
}

internal class UnlimitedStorage : CacheStorage {

    private val store = ConcurrentMap<Url, MutableSet<CachedResponseData>>()

    override suspend fun store(url: Url, data: CachedResponseData) {
        val cache = store.computeIfAbsent(url) { ConcurrentSet() }
        if (!cache.add(data)) {
            cache.remove(data)
            cache.add(data)
        }
    }

    override suspend fun find(url: Url, varyKeys: Map<String, String>): CachedResponseData? {
        val data = store.computeIfAbsent(url) { ConcurrentSet() }
        return data.find {
            varyKeys.all { (key, value) -> it.varyKeys[key] == value }
        }
    }

    override suspend fun findAll(url: Url): Set<CachedResponseData> = store[url] ?: emptySet()

    override suspend fun remove(url: Url, varyKeys: Map<String, String>) {
        store[url]?.remove(varyKeys) { it.varyKeys }
    }

    override suspend fun removeAll(url: Url) {
        store.remove(url)
    }
}
