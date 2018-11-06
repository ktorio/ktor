package io.ktor.util

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
