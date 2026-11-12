/*
 * Copyright 2014-2026 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

import io.ktor.utils.io.*

/**
 * [SynchronizedObject] from `kotlinx.atomicfu.locks`
 *
 * [SynchronizedObject] is designed for inheritance. You write `class MyClass : SynchronizedObject()`
 * and then use `synchronized(instance) { ... }` extension function similarly to the synchronized function
 * from the standard library that is available for JVM.
 * The [SynchronizedObject] superclass gets erased (transformed to Any) on JVM and JS,
 * with `synchronized` leaving no trace in the code on JS and getting replaced with built-in monitors for locking on JVM.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.SynchronizedObject)
 */
@InternalAPI
public actual open class SynchronizedObject {
    private val delegate: kotlinx.atomicfu.locks.SynchronizedObject =
        kotlinx.atomicfu.locks.SynchronizedObject()

    /**
     * Acquires the lock. If the lock is already held by another thread, the current thread
     * will block until it can acquire the lock.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.SynchronizedObject.lock)
     */
    public fun lock(): Unit = delegate.lock()

    /**
     * Attempts to acquire the lock. If the lock is available, it will be acquired, and this
     * function will return true. If the lock is already held by another thread, this function
     * will return false immediately without blocking.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.SynchronizedObject.tryLock)
     *
     * @return true if the lock was acquired, false otherwise.
     */
    public fun tryLock(): Boolean = delegate.tryLock()

    /**
     * Releases the lock. If the current thread holds the lock, it will be released, allowing
     * other threads to acquire it.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.SynchronizedObject.unlock)
     */
    public fun unlock(): Unit = delegate.unlock()
}

@InternalAPI
public actual typealias ReentrantLock = SynchronizedObject

/**
 * Creates a new [ReentrantLock] instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.reentrantLock)
 */
@InternalAPI
public actual fun reentrantLock(): ReentrantLock = ReentrantLock()

/**
 * Executes the given [block] of code while holding the specified [ReentrantLock]. This function
 * simplifies the process of acquiring and releasing a lock safely.
 *
 * Usage:
 * ```
 * val lock = reentrantLock()
 * lock.withLock {
 *     // Critical section of code
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.withLock)
 */
@InternalAPI
public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T {
    lock()
    try {
        return block()
    } finally {
        unlock()
    }
}

/**
 * Executes the given [block] of code within a synchronized block, using the specified
 * [SynchronizedObject] as the synchronization object. This function simplifies the process
 * of creating synchronized blocks safely.
 *
 * Usage:
 * ```
 * val syncObject = SynchronizedObject()
 * synchronized(syncObject) {
 *     // Critical section of code
 * }
 * ```
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.synchronized)
 */
@InternalAPI
public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T {
    lock.lock()
    try {
        return block()
    } finally {
        lock.unlock()
    }
}
