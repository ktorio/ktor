/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

public expect open class SynchronizedObject()

public expect fun reentrantLock(): ReentrantLock

public expect class ReentrantLock {
    public fun lock()
    public fun tryLock(): Boolean
    public fun unlock()
}

public expect inline fun <T> ReentrantLock.withLock(block: () -> T): T

public expect inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T
