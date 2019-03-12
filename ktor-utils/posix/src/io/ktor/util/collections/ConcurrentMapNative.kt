package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
actual class ConcurrentMap<Key, Value> : MutableMap<Key, Value> by mutableMapOf() {
    actual fun getOrDefault(key: Key, block: () -> Value): Value {
        get(key)?.let { return it }
        return block().also { put(key, it) }
    }
}
