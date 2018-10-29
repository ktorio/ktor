package io.ktor.util

@InternalAPI
actual fun <T> Set<T>.unmodifiable(): Set<T> = this
