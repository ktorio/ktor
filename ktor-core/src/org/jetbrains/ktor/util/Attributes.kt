package org.jetbrains.ktor.util

import java.util.concurrent.*

public open class AttributeKey<T>

public class Attributes {
    private val map by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConcurrentHashMap<AttributeKey<*>, Any?>()
    }

    @Suppress("UNCHECKED_CAST")
    operator fun <T : Any> get(key: AttributeKey<T>): T = map[key] as T? ?: throw IllegalStateException("No instance for key $key")

    operator fun contains(key: AttributeKey<*>) = map.containsKey(key)

    fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T = map.computeIfAbsent(key) { block() } as T

    val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}
