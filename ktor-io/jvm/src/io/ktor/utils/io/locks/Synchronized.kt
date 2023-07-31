/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

public actual typealias SynchronizedObject = Any

public actual fun reentrantLock(): ReentrantLock = ReentrantLock()

public actual typealias ReentrantLock = java.util.concurrent.locks.ReentrantLock

public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T =
    kotlin.synchronized(lock, block)
