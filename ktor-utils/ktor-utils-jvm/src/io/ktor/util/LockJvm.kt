@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

import java.util.concurrent.locks.*

@InternalAPI
actual class Lock {
    private val mutex = ReentrantLock()

    actual fun lock() {
        mutex.lock()
    }
    actual fun unlock() {
        mutex.unlock()
    }
}
