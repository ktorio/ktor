package io.ktor.util

import java.util.*

actual fun <T> Set<T>.unmodifiable(): Set<T> = Collections.unmodifiableSet(this)
