package io.ktor.util

@Suppress("KDocMissingDocumentation", "unused")
@Deprecated("Use stdlib's forEach instead", level = DeprecationLevel.ERROR)
inline fun <K, V> Map<K, V>.forEach(body: (K, V) -> Unit): Unit = TODO()

/**
 * Create an instance of case insensitive mutable map. For internal use only.
 */
@InternalAPI
fun <Value> caseInsensitiveMap(): MutableMap<String, Value> = CaseInsensitiveMap()

/**
 * Freeze selected set. May do nothing on some platforms.
 */
@InternalAPI
expect fun <T> Set<T>.unmodifiable(): Set<T>
