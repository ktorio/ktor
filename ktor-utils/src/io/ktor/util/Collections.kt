package io.ktor.util

inline fun <K, V> Map<K, V>.forEach(body: (K, V) -> Unit): Unit = forEach { (key, value) -> body(key, value) }

fun <Value> caseInsensitiveMap(): MutableMap<String, Value> = CaseInsensitiveMap()

/**
 * Freeze selected set
 */
expect fun <T> Set<T>.unmodifiable(): Set<T>
