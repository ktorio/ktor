package io.ktor.compat

import io.ktor.util.*
import java.util.*


actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)

actual fun <Value> caseInsensitiveMap(capacity: Int): MutableMap<String, Value> =
    CaseInsensitiveMap(initialCapacity = capacity)
