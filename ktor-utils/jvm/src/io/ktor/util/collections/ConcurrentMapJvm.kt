package io.ktor.util.collections

import io.ktor.util.*
import java.util.concurrent.*

@InternalAPI
actual class ConcurrentMap<Key, Value> : MutableMap<Key, Value> by ConcurrentHashMap<Key, Value>() {
    actual fun getOrDefault(key: Key, block: () -> Value): Value {
        get(key)?.let { return it }

        val result = block()
        return putIfAbsent(key, result) ?: result
    }
}
