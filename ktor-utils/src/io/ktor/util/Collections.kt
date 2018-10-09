package io.ktor.util

@Deprecated("Use stdlib's forEach instead")
inline fun <K, V> Map<K, V>.forEach(body: (K, V) -> Unit): Unit = forEach { (key, value) -> body(key, value) }

@InternalAPI
fun <Value> caseInsensitiveMap(): MutableMap<String, Value> = CaseInsensitiveMap()

/**
 * Freeze selected set
 */
@InternalAPI
expect fun <T> Set<T>.unmodifiable(): Set<T>
