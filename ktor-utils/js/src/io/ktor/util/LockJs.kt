package io.ktor.util

@InternalAPI
actual class Lock actual constructor() {
    actual fun lock() {}
    actual fun unlock() {}
}

@InternalAPI
actual class ReadWriteLock actual constructor() {
    actual fun readLock(): LockTicket = LockTicketInstance
    actual fun writeLock(): LockTicket = LockTicketInstance
}

private val LockTicketInstance = LockTicket()

@InternalAPI
actual class LockTicket {
    actual fun unlock() {}
}
