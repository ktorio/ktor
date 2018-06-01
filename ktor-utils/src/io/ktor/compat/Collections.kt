package io.ktor.compat


inline fun <K, V> Map<K, V>.forEach(body: (K, V) -> Unit): Unit = forEach { (key: K, value: V) -> body(key, value) }

expect fun <Value> caseInsensitiveMap(capacity: Int = 16): MutableMap<String, Value>

expect fun <T> Set<T>.unmodifiable(): Set<T>
