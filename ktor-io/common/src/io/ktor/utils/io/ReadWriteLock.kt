/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.utils.io

import kotlinx.atomicfu.*
import kotlinx.coroutines.*

/**
 * Coroutine suspension implementation of ReadWriteLock.
 *
 * Allows for coroutines to suspend while waiting for content on either side of read/write.
 */
@InternalAPI
public class ReadWriteLock {

    private val readDeferred: AtomicDeferred<Unit> = atomic(null)
    private val writeDeferred: AtomicDeferred<Unit> = atomic(null)

    /**
     * Suspends until the next write task completes.
     */
    public suspend fun waitForRead() {
        readDeferred.getOrSet().await()
    }

    /**
     * Suspends until the next read task completes.
     */
    public suspend fun waitForWrite() {
        writeDeferred.getOrSet().await()
    }

    /**
     * Resumes the read coroutines.
     */
    public fun resumeRead() {
        readDeferred.completeAndClear(Unit)
    }

    /**
     * Resumes the writer coroutines.
     */
    public fun resumeWrite() {
        writeDeferred.completeAndClear(Unit)
    }

    /**
     * Completes any suspended coroutines.
     */
    public fun close(cause: Throwable? = null) {
        when(cause) {
            null -> {
                readDeferred.completePermanently(Unit)
                writeDeferred.completePermanently(Unit)
            }
            else -> {
                readDeferred.completeExceptionally(cause)
                writeDeferred.completeExceptionally(cause)
            }
        }
    }

    /**
     * Atomically completes and clears deferred, so waiting threads can resume.
     *
     * If the deferred is already completed, we leave it alone, assuming it is closed.
     */
    private inline fun <T> AtomicDeferred<T>.completeAndClear(value: T) {
        getAndUpdate { it?.takeIf { it.isCompleted } }?.complete(value)
    }

    /**
     * Completes the deferred and does not clear - used to close.
     */
    private inline fun <T> AtomicDeferred<T>.completePermanently(value: T) {
        getOrSet().complete(value)
    }

    /**
     * Completes the deferred with an exception so that dependent tasks will throw.
     */
    private inline fun <T> AtomicDeferred<T>.completeExceptionally(throwable: Throwable) {
        getOrSet().completeExceptionally(throwable)
    }

    /**
     * Installs a new deferred if the current value is not null.
     */
    private inline fun <T> AtomicDeferred<T>.getOrSet(): CompletableDeferred<T> =
        updateAndGet { it ?: CompletableDeferred() }!!
}

internal typealias AtomicDeferred<T> = AtomicRef<CompletableDeferred<T>?>
