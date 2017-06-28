package org.jetbrains.ktor.util

import java.util.concurrent.*

open class AttributeKey<T>(val name: String) {
    override fun toString(): String = if (name.isEmpty())
        super.toString()
    else
        "AttributeKey: $name"
}

class Attributes {
    private val map = ConcurrentHashMap<AttributeKey<*>, Any?>()

    operator fun <T : Any> get(key: AttributeKey<T>): T = getOrNull(key) ?: throw IllegalStateException("No instance for key $key")

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    operator fun contains(key: AttributeKey<*>) = map.containsKey(key)

    fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        return map.computeIfAbsent(key) { block() } as T
    }

    val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}
