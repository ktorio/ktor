/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.utils.io.internal

import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * AwaitingSlot class is used to suspend coroutines until a condition is met, or it is resumed/canceled.
 * If the second waiter comes, it evicts the first waiter.
 *
 * @constructor Creates an instance of AwaitingSlot.
 */
internal class AwaitingSlot {
    private val suspension: AtomicRef<CancellableContinuation<Unit>?> = atomic(null)

    /**
     * Wait for other [sleepWhile] or resume.
     */
    suspend fun sleepWhile(sleepCondition: () -> Boolean) {
        while (sleepCondition()) {
            trySuspend(sleepCondition)
        }
    }

    /**
     * Resume waiter.
     */
    fun resume() {
        val continuation = suspension.getAndUpdate {
            if (it == CLOSED) CLOSED else null
        }

        continuation?.resume(Unit)
    }

    /**
     * Cancel waiter.
     */
    fun close(cause: Throwable?) {
        val closeContinuation = if (cause != null) ClosedSlot(cause) else CLOSED
        val continuation = suspension.getAndSet(closeContinuation) ?: return
        if (continuation is ClosedSlot) return

        if (cause != null) {
            continuation.resumeWithException(cause)
        } else {
            continuation.resume(Unit)
        }
    }

    private suspend fun trySuspend(sleepCondition: () -> Boolean): Boolean {
        var suspended = false

        suspendCancellableCoroutine {
            val published = suspension.compareAndSet(null, it)
            if (!published) {
                it.resume(Unit)
                return@suspendCancellableCoroutine
            }

            if (sleepCondition()) {
                suspended = true
            } else {
                suspension.getAndSet(null)?.resume(Unit)
            }
        }

        return suspended
    }
}

private val CLOSED = ClosedSlot(null)

private class ClosedSlot(val cause: Throwable?) : CancellableContinuation<Unit> {
    override val context: CoroutineContext = EmptyCoroutineContext
    override val isActive: Boolean = false
    override val isCancelled: Boolean = cause != null
    override val isCompleted: Boolean = true

    override fun cancel(cause: Throwable?): Boolean {
        return false
    }

    @InternalCoroutinesApi
    override fun completeResume(token: Any) {
        checkClosed()
    }

    @InternalCoroutinesApi
    override fun initCancellability() = Unit

    override fun invokeOnCancellation(handler: CompletionHandler) = Unit

    @InternalCoroutinesApi
    override fun tryResumeWithException(exception: Throwable): Any? {
        checkClosed()
        return null
    }

    @ExperimentalCoroutinesApi
    override fun CoroutineDispatcher.resumeUndispatchedWithException(exception: Throwable) {
        checkClosed()
    }

    @ExperimentalCoroutinesApi
    override fun CoroutineDispatcher.resumeUndispatched(value: Unit) {
        checkClosed()
    }

    @InternalCoroutinesApi
    override fun tryResume(value: Unit, idempotent: Any?, onCancellation: ((cause: Throwable) -> Unit)?): Any? {
        checkClosed()
        return null
    }

    @InternalCoroutinesApi
    override fun tryResume(value: Unit, idempotent: Any?): Any? {
        checkClosed()
        return null
    }

    @ExperimentalCoroutinesApi
    override fun resume(value: Unit, onCancellation: ((cause: Throwable) -> Unit)?) = checkClosed()

    override fun resumeWith(result: Result<Unit>) = checkClosed()

    override fun hashCode(): Int = 777

    fun checkClosed() {
        if (cause != null) throw cause
    }
}
