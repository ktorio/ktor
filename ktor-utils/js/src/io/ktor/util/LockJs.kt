/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
