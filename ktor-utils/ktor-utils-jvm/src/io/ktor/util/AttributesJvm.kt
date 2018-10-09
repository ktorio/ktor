package io.ktor.util

import java.util.concurrent.*

actual fun Attributes(): Attributes = AttributesJvm()

private class AttributesJvm : Attributes {
    private val map = ConcurrentHashMap<AttributeKey<*>, Any?>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getOrNull(key: AttributeKey<T>): T? = map[key] as T?

    override operator fun contains(key: AttributeKey<*>): Boolean = map.containsKey(key)

    override fun <T : Any> put(key: AttributeKey<T>, value: T) {
        map[key] = value
    }

    override fun <T : Any> remove(key: AttributeKey<T>) {
        map.remove(key)
    }

    /**
     * Gets a value of the attribute for the specified [key], or calls supplied [block] to compute its value.
     * Note: [block] could be eventually evaluated twice for the same key.
     * TODO: To be discussed. Workaround for android < API 24.
     */
    override fun <T : Any> computeIfAbsent(key: AttributeKey<T>, block: () -> T): T {
        @Suppress("UNCHECKED_CAST")
        map[key]?.let { return it as T }
        val result = block()
        @Suppress("UNCHECKED_CAST")
        return (map.putIfAbsent(key, result) ?: result) as T
    }

    override val allKeys: List<AttributeKey<*>>
        get() = map.keys.toList()
}
