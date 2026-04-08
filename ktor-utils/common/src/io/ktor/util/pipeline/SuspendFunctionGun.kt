/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util.pipeline

import io.ktor.util.*
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.Continuation
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.intercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.coroutines.resumeWithException

/**
 * Executes pipeline [interceptors] with minimal overhead on the hot path.
 *
 * Instead of resuming each interceptor through a separate continuation step, it continues directly
 * to the next one until one suspends and stores only the state needed to resume later.
 * This reduces allocations and avoids unnecessary rescheduling through the dispatcher.
 *
 * ## Implementation Notes
 *
 * ### Shared continuation
 *
 * All interceptors are started with the same [continuation] instance, so they all complete back
 * into the same pipeline driver and the whole pipeline can be advanced as one state machine.
 *
 * ### Saved caller continuations
 *
 * When [proceed] invokes the next interceptor, it saves the caller continuation in [suspensions].
 * When downstream work completes, [resumeRootWith] pops and resumes the top saved continuation, so
 * each [proceed] returns to its caller after later interceptors finish.
 *
 * ### Current coroutine context
 *
 * The shared [continuation] resolves [Continuation.context] from the top saved continuation, so
 * each interceptor sees the coroutine context of the caller that last invoked [proceed].
 *
 * ### Conditional redispatch
 *
 * [resumeRootWith] resumes saved continuations directly when execution is already in the right
 * thread, and otherwise resumes them through the intercepted continuation.
 */
internal class SuspendFunctionGun<TSubject : Any, TContext : Any>(
    initial: TSubject,
    context: TContext,
    private val interceptors: List<PipelineInterceptor<TSubject, TContext>>
) : PipelineContext<TSubject, TContext>(context) {

    override val coroutineContext: CoroutineContext get() = continuation.context

    // this is impossible to inline because of property name clash
    // between PipelineContext.context and Continuation.context
    internal val continuation: Continuation<Unit> = object : Continuation<Unit>, CoroutineStackFrame {
        override val callerFrame: CoroutineStackFrame? get() = peekContinuation() as? CoroutineStackFrame

        var currentIndex: Int = Int.MIN_VALUE

        override fun getStackTraceElement(): StackTraceElement? = null

        private fun peekContinuation(): Continuation<*>? {
            if (currentIndex == Int.MIN_VALUE) currentIndex = lastSuspensionIndex
            if (currentIndex < 0) {
                currentIndex = Int.MIN_VALUE
                return null
            }
            // this is only invoked by debug agent during job state probes
            // currentIndex is non-volatile intentionally
            // and the list of continuations is not synchronized too
            // so this is not guaranteed to work properly (may produce incorrect trace),
            // but the only we care is to not crash here
            // and simply return StackWalkingFailedFrame on any unfortunate accident

            try {
                val result = suspensions[currentIndex] ?: return StackWalkingFailedFrame
                currentIndex -= 1
                return result
            } catch (_: Throwable) {
                return StackWalkingFailedFrame
            }
        }

        override val context: CoroutineContext
            get() {
                for (index in lastSuspensionIndex downTo 0) {
                    val cont = suspensions[index]
                    if (cont !== this && cont != null) return cont.context
                }
                error("Not started")
            }

        override fun resumeWith(result: Result<Unit>) {
            result.onFailure { exception ->
                resumeRootWith(Result.failure(exception))
                return
            }

            loop(direct = false)
        }
    }

    override var subject: TSubject = initial

    private val suspensions: Array<Continuation<TSubject>?> = arrayOfNulls(interceptors.size)
    private var lastSuspensionIndex: Int = -1
    private var index = 0

    override fun finish() {
        index = interceptors.size
    }

    override suspend fun proceed(): TSubject = suspendCoroutineUninterceptedOrReturn { continuation ->
        if (index == interceptors.size) return@suspendCoroutineUninterceptedOrReturn subject

        addContinuation(continuation)

        if (loop(direct = true)) {
            discardLastRootContinuation()
            return@suspendCoroutineUninterceptedOrReturn subject
        }

        COROUTINE_SUSPENDED
    }

    override suspend fun proceedWith(subject: TSubject): TSubject {
        this.subject = subject
        return proceed()
    }

    override suspend fun execute(initial: TSubject): TSubject {
        index = 0
        if (index == interceptors.size) return initial
        subject = initial

        check(lastSuspensionIndex < 0) { "Already started" }
        return proceed()
    }

    /**
     * Runs the remaining [interceptors] until one suspends or the pipeline completes.
     * Returns `true` when the caller can return [subject] immediately, without going through
     * [resumeRootWith].
     *
     * [direct] is used when [loop] is entered from [proceed], while execution is still moving
     * forward through [interceptors] and completion can be returned directly to the current caller.
     * When [loop] is entered later from the shared [continuation], completion must be delivered
     * through [resumeRootWith] instead.
     */
    private fun loop(direct: Boolean): Boolean {
        do {
            val currentIndex = index // it is important to read index every time
            if (currentIndex == interceptors.size) {
                if (!direct) {
                    resumeRootWith(Result.success(subject))
                    return false
                }

                return true
            }

            index = currentIndex + 1 // it is important to increase it before function invocation
            val next = interceptors[currentIndex]

            try {
                val result = pipelineStartCoroutineUninterceptedOrReturn(next, this, subject, continuation)
                if (result === COROUTINE_SUSPENDED) return false
            } catch (cause: Throwable) {
                resumeRootWith(Result.failure(cause))
                return false
            }
        } while (true)
    }

    private fun resumeRootWith(result: Result<TSubject>) {
        check(lastSuspensionIndex >= 0) { "No more continuations to resume" }
        val next = suspensions[lastSuspensionIndex]!!
        suspensions[lastSuspensionIndex--] = null

        // Dispatch only when the continuation's dispatcher requires it.
        // When already on the correct thread, resume unintercepted to avoid a second updateThreadContext call —
        // the incoming dispatch already applied the context.
        val toResume = when (val interceptor = next.context[ContinuationInterceptor]) {
            is CoroutineDispatcher -> if (interceptor.isDispatchNeeded(next.context)) next.intercepted() else next
            else -> next.intercepted()
        }

        when (val exception = result.exceptionOrNull()) {
            null -> toResume.resumeWith(result)
            else -> {
                val recoveredException = recoverStackTraceBridge(exception, next)
                toResume.resumeWithException(recoveredException)
            }
        }
    }

    private fun discardLastRootContinuation() {
        check(lastSuspensionIndex >= 0) { "No more continuations to resume" }
        suspensions[lastSuspensionIndex--] = null
    }

    internal fun addContinuation(continuation: Continuation<TSubject>) {
        suspensions[++lastSuspensionIndex] = continuation
    }
}
