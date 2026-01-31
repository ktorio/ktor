/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Reusable suspension mechanism to avoid per-suspension allocations in ByteChannel.
// ABOUTME: Uses atomic state machine to cache continuation across multiple suspend/resume cycles.

package io.ktor.utils.io

import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

/**
 * Reusable suspension mechanism that avoids allocating new continuation objects per suspension.
 *
 * Unlike regular suspendCancellableCoroutine, this implementation:
 * - Reuses the same object across multiple suspend/resume cycles
 * - Avoids lambda allocation for the suspension block
 * - Handles job cancellation properly
 *
 * Limitations:
 * - Can only be resumed once per suspension cycle
 * - Should be used only for single-waiter scenarios (one reader OR one writer at a time)
 */
internal expect class ReusableSuspension() : Continuation<Unit> {
    /**
     * The coroutine context of the currently suspended continuation, or empty if not suspended.
     */
    override val context: CoroutineContext

    /**
     * Resumes the suspended coroutine with the given result.
     * This is the standard Continuation interface method used internally.
     */
    override fun resumeWith(result: Result<Unit>)

    /**
     * Attempts to suspend the coroutine. Called from suspendCoroutineUninterceptedOrReturn.
     *
     * @param continuation The actual continuation to resume later
     * @return COROUTINE_SUSPENDED if suspended, or Unit if already resumed
     */
    fun trySuspend(continuation: Continuation<Unit>): Any

    /**
     * Resumes the suspended coroutine with success.
     */
    fun resume()

    /**
     * Resumes the suspended coroutine with an exception.
     */
    fun resumeWithException(cause: Throwable)

    /**
     * Closes this suspension with a cause, used when the channel is closed.
     */
    fun close(cause: Throwable?)

    /**
     * Returns true if there's a continuation waiting to be resumed.
     * Used to detect stale slot state after cancellation.
     */
    fun isWaiting(): Boolean
}
