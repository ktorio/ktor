@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

import java.util.concurrent.locks.*
import java.util.concurrent.locks.Lock

@InternalAPI
actual class Lock {
    private val lock = ReentrantLock()

    actual fun lock() {
        lock.lock()
    }
    actual fun unlock() {
        lock.unlock()
    }
}

@InternalAPI
actual class ReadWriteLock actual constructor() {
    private val lock = ReentrantReadWriteLock()

    actual fun readLock(): LockTicket = LockTicket(lock.readLock())

    actual fun writeLock(): LockTicket = LockTicket(lock.writeLock())
}

actual class LockTicket(private val lock: Lock) {
    init {
        lock.lock()
    }

    actual fun unlock() {
        lock.unlock()
    }
}
