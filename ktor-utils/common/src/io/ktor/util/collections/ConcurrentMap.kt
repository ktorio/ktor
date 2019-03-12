package io.ktor.util.collections

import io.ktor.util.*

@InternalAPI
expect class ConcurrentMap<Key, Value>() : MutableMap<Key, Value> {
    fun getOrDefault(key: Key, block: () -> Value): Value
}
