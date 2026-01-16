/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: Reusable suspension mechanism to avoid per-suspension allocations in ByteChannel.
// ABOUTME: Uses atomic state machine to cache continuation across multiple suspend/resume cycles.

package io.ktor.utils.io

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

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
internal class ReusableSuspension : Continuation<Unit> {
    // State can be:
    // - null: idle, ready for suspension
    // - Continuation<Unit>: suspended, waiting for resume
    // - RESUMED: already resumed, return immediately
    // - Throwable: resumed with exception
    private val state = atomic<Any?>(null)

    // Tracks job cancellation handler for the current suspension
    private val jobHandler = atomic<JobCancellationHandler?>(null)

    override val context: CoroutineContext
        get() = (state.value as? Continuation<*>)?.context ?: EmptyCoroutineContext

    /**
     * Attempts to suspend the coroutine. Called from suspendCoroutineUninterceptedOrReturn.
     *
     * @param continuation The actual continuation to resume later
     * @return COROUTINE_SUSPENDED if suspended, or Unit if already resumed
     */
    fun trySuspend(continuation: Continuation<Unit>): Any {
        while (true) {
            when (val current = state.value) {
                null -> {
                    // Idle state - try to store continuation and suspend
                    if (state.compareAndSet(null, continuation)) {
                        setupJobCancellation(continuation.context)
                        return COROUTINE_SUSPENDED
                    }
                    // CAS failed, retry
                }
                RESUMED -> {
                    // Already resumed - reset to idle and return immediately
                    if (state.compareAndSet(RESUMED, null)) {
                        return Unit
                    }
                    // CAS failed, retry
                }
                is Throwable -> {
                    // Resumed with exception - reset to idle and throw
                    if (state.compareAndSet(current, null)) {
                        throw current
                    }
                    // CAS failed, retry
                }
                else -> {
                    // Continuation already stored - this shouldn't happen in correct usage
                    // Return immediately to avoid double suspension
                    return Unit
                }
            }
        }
    }

    /**
     * Resumes the suspended coroutine with success.
     */
    fun resume() {
        resumeWith(Result.success(Unit))
    }

    /**
     * Resumes the suspended coroutine with an exception.
     */
    fun resumeWithException(cause: Throwable) {
        resumeWith(Result.failure(cause))
    }

    override fun resumeWith(result: Result<Unit>) {
        while (true) {
            when (val current = state.value) {
                null -> {
                    // Not yet suspended - store result for later pickup
                    val newState = result.exceptionOrNull() ?: RESUMED
                    if (state.compareAndSet(null, newState)) {
                        return
                    }
                    // CAS failed, retry
                }
                is Continuation<*> -> {
                    // Suspended - resume the continuation
                    if (state.compareAndSet(current, null)) {
                        disposeJobHandler()
                        @Suppress("UNCHECKED_CAST")
                        (current as Continuation<Unit>).resumeWith(result)
                        return
                    }
                    // CAS failed, retry
                }
                else -> {
                    // Already resumed or has exception - ignore
                    return
                }
            }
        }
    }

    /**
     * Closes this suspension with a cause, used when the channel is closed.
     */
    fun close(cause: Throwable?) {
        disposeJobHandler()
        if (cause != null) {
            resumeWithException(cause)
        } else {
            resume()
        }
    }

    private fun setupJobCancellation(context: CoroutineContext) {
        val job = context[Job] ?: return

        // Check if we already have a handler for this job
        val currentHandler = jobHandler.value
        if (currentHandler?.job === job) return

        // Create new handler
        val newHandler = JobCancellationHandler(job)
        val oldHandler = jobHandler.getAndSet(newHandler)
        oldHandler?.dispose()

        // If job is already cancelled, the handler will be invoked synchronously
    }

    private fun disposeJobHandler() {
        jobHandler.getAndSet(null)?.dispose()
    }

    /**
     * Handles job cancellation by resuming the suspension with the cancellation cause.
     */
    private inner class JobCancellationHandler(val job: Job) {
        private var handle: DisposableHandle? = null

        init {
            @OptIn(InternalCoroutinesApi::class)
            handle = job.invokeOnCompletion(onCancelling = true) { cause ->
                onJobCompletion(cause)
            }
        }

        private fun onJobCompletion(cause: Throwable?) {
            // Remove ourselves from the handler slot
            jobHandler.compareAndSet(this, null)
            dispose()

            if (cause != null) {
                // Only resume if we're still the active suspension for this job
                val current = state.value
                if (current is Continuation<*> && current.context[Job] === job) {
                    resumeWithException(cause)
                }
            }
        }

        fun dispose() {
            handle?.dispose()
            handle = null
        }
    }

    companion object {
        // Sentinel object indicating successful resume before suspension
        private val RESUMED = Any()
    }
}
