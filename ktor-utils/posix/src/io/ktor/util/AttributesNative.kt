/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.*
import kotlin.concurrent.Volatile

/**
 * Create native specific attributes instance.
 *
 * As on the JVM, only [concurrent] attributes are safe to use from several threads at once. Either kind makes its
 * map on the first write: most attributes, such as a call's or a pipeline's, are never written to.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Attributes)
 */
public actual fun Attributes(concurrent: Boolean): Attributes =
    if (concurrent) ConcurrentAttributesNative() else HashMapAttributesNative()

private class HashMapAttributesNative : Attributes {
    private var map: HashMap<AttributeKey<*>, Any>? = null

    private fun map(): HashMap<AttributeKey<*>, Any> = map ?: HashMap<AttributeKey<*>, Any>().also { map = it }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map?.get(key) as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map?.containsKey(key) == true

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map()[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map?.remove(key)
    }

    // As the JVM's: block could run twice for one key
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        val map = map()
        (map[key] as T?)?.let { return it }
        val result = block()
        return (map.put(key, result) ?: result) as T
    }

    override val allKeys: List<AttributeKey<*>>
        get() = map?.keys?.toList() ?: emptyList()
}

// Guards the writes of every concurrent attributes, which are a pipeline's or a route's as it is set up
@OptIn(InternalAPI::class)
private val writes = SynchronizedObject()

// Copy on write: a read is a volatile load and a lookup in a map no thread changes, so the route-scoped plugin lookup
// every call makes takes no lock - under ConcurrentMap's, every core's calls took turns.
@OptIn(InternalAPI::class)
private class ConcurrentAttributesNative : Attributes {
    @Volatile
    private var map: Map<AttributeKey<*>, Any>? = null

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map?.get(key) as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map?.containsKey(key) == true

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        synchronized(writes) {
            map = copy().apply { put(key, value) }
        }
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        synchronized(writes) {
            if (map?.containsKey(key) == true) map = copy().apply { remove(key) }
        }
    }

    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T =
        getOrNull(key) ?: synchronized(writes) {
            getOrNull(key) ?: block().also { value -> map = copy().apply { put(key, value) } }
        }

    private fun copy(): HashMap<AttributeKey<*>, Any> = map?.let { HashMap(it) } ?: HashMap()

    override val allKeys: List<AttributeKey<*>>
        get() = map?.keys?.toList() ?: emptyList()
}
