package org.jetbrains.ktor.util

import java.util.concurrent.*

open class AttributeKey<out T>(val name: String) {
    override fun toString(): String = if (name.isEmpty())
        super.toString()
    else
        "AttributeKey: $name"
}

class Attributes {
    private var touched = false
    private val map by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        ConcurrentHashMap<AttributeKey<*>, Any?>()
    }

    operator fun <T : Any> get(key: AttributeKey<T>): T = getOrNull(key) ?: throw IllegalStateException("No instance for key $key")

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> getOrNull(key: AttributeKey<T>): T? = if (touched) map[key] as T? else null

    operator fun contains(key: AttributeKey<*>) = if (touched) map.containsKey(key) else false

    fun <T : Any> put(key: AttributeKey<T>, value: T) {
        touched = true
        map[key] = value
    }

    fun <T : Any> remove(key: AttributeKey<T>) {
        if (touched) {
            map.remove(key)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        touched = true
        return map.computeIfAbsent(key) { block() } as T
    }

    val allKeys: List<AttributeKey<Any?>>
        get() = if (touched) map.keys.toList() else emptyList()
}
