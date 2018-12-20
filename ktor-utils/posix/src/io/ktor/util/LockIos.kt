package io.ktor.util

import kotlinx.cinterop.*
import utils.*

@InternalAPI
actual class Lock {
    private val mutex = cValue<ktor_mutex_t>()

    init {
        ktor_mutex_create(mutex)
    }

    actual fun lock() {
        ktor_mutex_lock(mutex)
    }

    actual fun unlock() {
        ktor_mutex_unlock(mutex)
    }
}
