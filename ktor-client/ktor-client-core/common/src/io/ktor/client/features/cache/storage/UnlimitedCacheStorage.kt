/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.client.features.cache.storage

import io.ktor.client.features.cache.*
import io.ktor.http.*
import io.ktor.util.collections.*

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
        return data.find { it.varyKeys == varyKeys }
    }

    override fun findByUrl(url: Url): Set<HttpCacheEntry> = store[url] ?: emptySet()
}
