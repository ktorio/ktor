/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

// ABOUTME: JVM/Posix actual implementation of ReusableSuspension with zero allocation job cancellation.
// ABOUTME: Uses CompletionHandler interface to avoid lambda allocation on job completion callback.

package io.ktor.utils.io

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletionHandler
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.Job
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

internal actual class ReusableSuspension actual constructor() : Continuation<Unit> {
    // State can be:
    // - null: idle, ready for suspension
    // - Continuation<Unit>: suspended, waiting for resume
    // - RESUMED: already resumed, return immediately
    // - Throwable: resumed with exception
    private val state = atomic<Any?>(null)

    // Tracks job cancellation handler for the current suspension
    private val jobHandler = atomic<JobCancellationHandler?>(null)

    actual override val context: CoroutineContext
        get() = (state.value as? Continuation<*>)?.context ?: EmptyCoroutineContext

    actual fun trySuspend(continuation: Continuation<Unit>): Any {
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

    actual fun resume() {
        resumeWith(Result.success(Unit))
    }

    actual fun resumeWithException(cause: Throwable) {
        resumeWith(Result.failure(cause))
    }

    actual override fun resumeWith(result: Result<Unit>) {
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

    actual fun close(cause: Throwable?) {
        disposeJobHandler()
        if (cause != null) {
            resumeWithException(cause)
        } else {
            resume()
        }
    }

    actual fun isWaiting(): Boolean = state.value is Continuation<*>

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
     * Implements CompletionHandler interface directly to avoid lambda allocation.
     */
    private inner class JobCancellationHandler(val job: Job) : CompletionHandler {
        private var handle: DisposableHandle? = null

        init {
            @OptIn(InternalCoroutinesApi::class)
            handle = job.invokeOnCompletion(onCancelling = true, handler = this)
        }

        override fun invoke(cause: Throwable?) {
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
