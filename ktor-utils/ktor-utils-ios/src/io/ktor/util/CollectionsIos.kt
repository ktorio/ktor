package io.ktor.util

actual fun <T> Set<T>.unmodifiable(): Set<T> = this
