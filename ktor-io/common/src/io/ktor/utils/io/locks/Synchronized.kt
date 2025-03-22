/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
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
public expect open class SynchronizedObject()

/**
 * [ReentrantLock] from kotlinx.atomicfu.locks
 *
 * [ReentrantLock] is designed for delegation. You write `val lock = reentrantLock()` to construct its instance and
 * use `lock/tryLock/unlock` functions or `lock.withLock { ... }` extension function similarly to
 * the way jucl.ReentrantLock is used on JVM. On JVM it is a typealias to the later class, erased on JS.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.ReentrantLock)
 */
@InternalAPI
public expect class ReentrantLock {
    /**
     * Acquires the lock. If the lock is already held by another thread, the current thread
     * will block until it can acquire the lock.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.ReentrantLock.lock)
     */
    public fun lock()

    /**
     * Attempts to acquire the lock. If the lock is available, it will be acquired, and this
     * function will return true. If the lock is already held by another thread, this function
     * will return false immediately without blocking.
     *
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.ReentrantLock.tryLock)
     *
     * @return true if the lock was acquired, false otherwise.
     */
    public fun tryLock(): Boolean

    /**
     * Releases the lock. If the current thread holds the lock, it will be released, allowing
     * other threads to acquire it.
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.ReentrantLock.unlock)
     */
    public fun unlock()
}

/**
 * Creates a new [ReentrantLock] instance.
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.utils.io.locks.reentrantLock)
 */
@InternalAPI
public expect fun reentrantLock(): ReentrantLock

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
public expect inline fun <T> ReentrantLock.withLock(block: () -> T): T

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
public expect inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T
