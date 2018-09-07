package io.ktor.util

import kotlinx.cinterop.*
import platform.posix.*

@InternalAPI
actual class Lock {
    private val mutex = cValue<pthread_mutex_t>{}

    init {
        pthread_mutex_init(mutex, null)
    }

    actual fun lock() {
        pthread_mutex_lock(mutex)
    }

    actual fun unlock() {
        pthread_mutex_unlock(mutex)
    }
}
