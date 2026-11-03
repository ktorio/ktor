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
 * As on the JVM, only [concurrent] attributes are safe to use from several threads at once. Non-concurrent ones make
 * their map on the first write: most attributes, such as a call's, are never written to.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.Attributes)
 */
public actual fun Attributes(concurrent: Boolean): Attributes =
    if (concurrent) ConcurrentAttributesNative() else HashMapAttributesNative()

/**
 * Create native read-mostly attributes: copy on write, so a read takes no lock.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.util.readMostlyAttributes)
 */
@InternalAPI
public actual fun readMostlyAttributes(): Attributes = ReadMostlyAttributesNative()

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

private class ConcurrentAttributesNative : Attributes {
    private val map = ConcurrentMap<AttributeKey<*>, Any>(ATTRIBUTES_INITIAL_CAPACITY)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map.containsKey(key)

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        return map.computeIfAbsent(key, block) as T
    }

    override val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}

// Writes are rare, during setup: instances share these by identity, rather than one lock each or one for all
@OptIn(InternalAPI::class)
private val readMostlyWriteLocks = Array(16) { SynchronizedObject() }

// Copy on write, so reads take no lock: ConcurrentMap locks reads too, and every call looks up its plugins
@OptIn(InternalAPI::class)
private class ReadMostlyAttributesNative : Attributes {
    @Volatile
    private var map: Map<AttributeKey<*>, Any>? = null

    private val writeLock: SynchronizedObject
        get() {
            val hash = hashCode()
            return readMostlyWriteLocks[(hash xor (hash ushr 16)) and (readMostlyWriteLocks.size - 1)]
        }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map?.get(key) as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map?.containsKey(key) == true

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        synchronized(writeLock) {
            map = copy().apply { put(key, value) }
        }
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        synchronized(writeLock) {
            if (map?.containsKey(key) == true) map = copy().apply { remove(key) }
        }
    }

    // As on the JVM, block runs outside the lock: it may run twice, and the first value stored wins
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        getOrNull(key)?.let { return it }
        val value = block()
        return synchronized(writeLock) {
            map?.get(key) as T? ?: value.also { map = copy().apply { put(key, value) } }
        }
    }

    private fun copy(): HashMap<AttributeKey<*>, Any> = map?.let { HashMap(it) } ?: HashMap()

    override val allKeys: List<AttributeKey<*>>
        get() = map?.keys?.toList() ?: emptyList()
}
