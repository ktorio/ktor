package io.ktor.util

import java.util.*

@InternalAPI
actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)
