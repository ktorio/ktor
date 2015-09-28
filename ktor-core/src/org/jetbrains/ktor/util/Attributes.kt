package org.jetbrains.ktor.util

import java.util.concurrent.*

public open class AttributeKey<T>

public class Attributes {
    private val map by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConcurrentHashMap<AttributeKey<*>, Any?>()
    }

    @Suppress("UNCHECKED_CAST")
    public operator fun <T> get(key: AttributeKey<T>): T = map[key] as T

    public fun <T> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    @Suppress("UNCHECKED_CAST")
    public fun <T> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T = map.computeIfAbsent(key) { block() } as T

    public val allKeys: List<AttributeKey<*>>
        get() = map.keySet().toList()
}
