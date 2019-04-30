/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("KDocMissingDocumentation")

package io.ktor.util

@InternalAPI
expect class Lock() {
    fun lock()
    fun unlock()
}

@InternalAPI
inline fun <R> Lock.use(block: () -> R): R {
    try {
        lock()
        return block()
    } finally {
        unlock()
    }
}

@InternalAPI
expect class ReadWriteLock() {
    fun readLock(): LockTicket
    fun writeLock(): LockTicket
}

expect class LockTicket {
    fun unlock()
}
