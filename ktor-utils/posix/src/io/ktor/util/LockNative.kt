package io.ktor.util

import kotlinx.cinterop.*
import platform.linux.*
import utils.*
import kotlin.native.concurrent.*

@InternalAPI
actual class Lock {
    private val mutex = cValue<ktor_mutex_t>()

    init {
        freeze()
        ktor_mutex_create(mutex)
    }

    actual fun lock() {
        ktor_mutex_lock(mutex)
    }

    actual fun unlock() {
        ktor_mutex_unlock(mutex)
    }
}

@InternalAPI
actual class ReadWriteLock actual constructor() {
    private val lock = cValue<ktor_rwlock>()
    private val ticket = LockTicket(lock)

    init {
        freeze()
        ktor_rwlock_create(lock)
    }
    actual fun readLock(): LockTicket {
        ktor_rwlock_read(lock)
        return ticket
    }

    actual fun writeLock(): LockTicket {
        ktor_rwlock_read(lock)
        return ticket
    }
}

@InternalAPI
actual class LockTicket(private val lock: CValue<ktor_rwlock>) {
    actual fun unlock() {
        ktor_rwlock_unlock(lock)
    }
}

@InternalAPI
inline fun <R> LockTicket.use(block: () -> R): R {
    try {
        return block()
    } finally {
        unlock()
    }

}

@InternalAPI
inline fun <R> ReadWriteLock.read(block: () -> R): R = readLock().use(block)

@InternalAPI
inline fun <R> ReadWriteLock.write(block: () -> R): R = writeLock().use(block)
