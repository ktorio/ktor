package io.ktor.util.collections

actual class ConcurrentMap<Key, Value> : MutableMap<Key, Value> by mutableMapOf() {
    actual fun getOrDefault(key: Key, block: () -> Value): Value {
        get(key)?.let { return it }
        return block().also { put(key, it) }
    }
}
