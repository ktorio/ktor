package io.ktor.util

import java.util.*

/**
 * Wraps into an unmodifiable set
 */
@InternalAPI
actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)
