/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io.locks

public actual typealias SynchronizedObject = Any

@JsName(REENTRANT_LOCK)
public val Lock: ReentrantLock = ReentrantLock()

@Suppress("NOTHING_TO_INLINE")
public actual inline fun reentrantLock(): ReentrantLock = Lock

@Suppress("NOTHING_TO_INLINE")
public actual class ReentrantLock {
    public actual inline fun lock() {}
    public actual inline fun tryLock(): Boolean = true
    public actual inline fun unlock() {}
}

public actual inline fun <T> ReentrantLock.withLock(block: () -> T): T = block()

public actual inline fun <T> synchronized(lock: SynchronizedObject, block: () -> T): T = block()

internal const val REENTRANT_LOCK = "atomicfu\$reentrantLock"
