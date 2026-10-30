/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import io.ktor.util.collections.*
import io.ktor.utils.io.InternalAPI
import io.ktor.utils.io.locks.*
import kotlin.concurrent.Volatile

private const val ATTRIBUTES_INITIAL_CAPACITY = 32

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

// Guards only the making of a map, once per attributes that are ever written to
@OptIn(InternalAPI::class)
private val mapCreation = SynchronizedObject()

@OptIn(InternalAPI::class)
private class ConcurrentAttributesNative : Attributes {
    @Volatile
    private var map: ConcurrentMap<AttributeKey<*>, Any>? = null

    private fun map(): ConcurrentMap<AttributeKey<*>, Any> = map ?: synchronized(mapCreation) {
        map ?: ConcurrentMap<AttributeKey<*>, Any>(ATTRIBUTES_INITIAL_CAPACITY).also { map = it }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map?.get(key) as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map?.containsKey(key) == true

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map()[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map?.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        return map().computeIfAbsent(key, block) as T
    }

    override val allKeys: List<AttributeKey<*>>
        get() = map?.keys?.toList() ?: emptyList()
}
