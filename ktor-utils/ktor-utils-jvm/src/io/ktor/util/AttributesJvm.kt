package io.ktor.util

import java.util.concurrent.*

/**
 * Map of attributes accessible by [AttributeKey] in a typed manner
 */
actual class Attributes {
    private val map = ConcurrentHashMap<AttributeKey<*>, Any?>()

    actual operator fun <T : Any> get(key: AttributeKey<T>): T = getOrNull(key) ?: throw IllegalStateException("No instance for key $key")

    /**
     * Gets a value of the attribute for the specified [key], or return `null` if an attribute doesn't exist
     */
    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    /**
     * Checks if an attribute with the specified [key] exists
     */
    actual operator fun contains(key: AttributeKey<*>): Boolean = map.containsKey(key)

    /**
     * Creates or changes an attribute with the specified [key] using [value]
     */
    actual fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    /**
     * Removes an attribute with the specified [key]
     */
    actual fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    /**
     * Removes an attribute with the specified [key] and returns its current value, throws an exception if an attribute doesn't exist
     */
    actual fun <T : Any> take(key: AttributeKey<T>): T = get(key).also { map.remove(key) }

    /**
     * Removes an attribute with the specified [key] and returns its current value, returns `null` if an attribute doesn't exist
     */
    actual fun <T : Any> takeOrNull(key: AttributeKey<T>): T? = getOrNull(key).also { map.remove(key) }

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value
     */
    @Suppress("UNCHECKED_CAST")
    actual fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T =
        map.computeIfAbsent(key) { block() } as T

    /**
     * Returns [List] of all [AttributeKey] instances in this map
     */
    actual val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}
