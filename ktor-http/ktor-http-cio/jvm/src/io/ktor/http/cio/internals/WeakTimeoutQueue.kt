/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.cio.internals

import io.ktor.util.*
import io.ktor.util.internal.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

/**
 * It provides ability to cancel jobs and schedule coroutine with timeout. Unlike regular withTimeout
 * this implementation is never scheduling timer tasks but only checks for current time. This makes timeout measurement
 * much cheaper and doesn't require any watchdog thread.
 *
 * There are two limitations:
 *  - timeout period is fixed
 *  - job cancellation is not guaranteed if no new jobs scheduled
 *
 *  The last one limitation is generally unacceptable
 *  however in the particular use-case (closing IDLE connection) it is just fine
 *  as we really don't care about stalling IDLE connections if there are no more incoming
 */
@InternalAPI
@Suppress("KDocMissingDocumentation")
public class WeakTimeoutQueue(
    public val timeoutMillis: Long,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    private val head = LockFreeLinkedListHead()

    @kotlin.jvm.Volatile
    private var cancelled = false

    /**
     * Register [job] in this queue. It will be cancelled if doesn't complete in time.
     */
    public fun register(job: Job): Registration {
        val now = clock()
        val head = head
        if (cancelled) throw CancellationException()

        val cancellable = JobTask(now + timeoutMillis, job)
        head.addLast(cancellable)

        process(now, head, cancelled)
        if (cancelled) {
            cancellable.cancel()
            throw CancellationException()
        }

        return cancellable
    }

    /**
     * Cancel all registered timeouts
     */
    public fun cancel() {
        cancelled = true
        process()
    }

    /**
     * Process and cancel all jobs that are timed out
     */
    public fun process() {
        process(clock(), head, cancelled)
    }

    /**
     * Counts registered jobs, for testing purpose only
     */
    internal fun count(): Int {
        var count = 0
        head.forEach<Cancellable> { count++ }
        return count
    }

    /**
     * Execute [block] and cancel if doesn't complete in time.
     * Unlike the regular kotlinx.coroutines withTimeout,
     * this also checks for cancellation first and fails immediately.
     */
    public suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T {
        return suspendCoroutineUninterceptedOrReturn { rawContinuation ->
            if (!rawContinuation.context.isActive) {
                // fast-path for cancellation with no continuation wrapping
                checkCancellation(rawContinuation)
            }
            val continuation = rawContinuation.intercepted()

            val wrapped = WeakTimeoutCoroutine(continuation.context, continuation)
            val handle = register(wrapped)
            wrapped.invokeOnCompletion(handle)

            val result = try {
                if (wrapped.isCancelled) {
                    @OptIn(InternalCoroutinesApi::class)
                    throw wrapped.getCancellationException()
                } else {
                    block.startCoroutineUninterceptedOrReturn(receiver = wrapped, completion = wrapped)
                }
            } catch (t: Throwable) {
                if (wrapped.tryComplete()) {
                    handle.dispose()
                    throw t
                } else COROUTINE_SUSPENDED
            }

            if (result !== COROUTINE_SUSPENDED && wrapped.tryComplete()) {
                handle.dispose()
            }

            result
        }
    }

    private fun <T> checkCancellation(continuation: Continuation<T>) {
        continuation.context[Job]?.let { job ->
            if (job.isCancelled) {
                @OptIn(InternalCoroutinesApi::class)
                throw job.getCancellationException()
            }
        }
    }

    private fun process(now: Long, head: LockFreeLinkedListHead, cancelled: Boolean) {
        while (true) {
            val p = head.next as? Cancellable ?: break
            if (!cancelled && p.deadline > now) break

            if (p.isActive && p.remove()) {
                p.cancel()
            }
        }
    }

    /**
     * [register] function result
     */
    public interface Registration : CompletionHandler, DisposableHandle {
        override fun invoke(cause: Throwable?) {
            dispose()
        }
    }

    private abstract class Cancellable(
        val deadline: Long
    ) : LockFreeLinkedListNode(), Registration {
        open val isActive: Boolean
            get() = !isRemoved

        abstract fun cancel()

        override fun dispose() {
            remove()
        }
    }

    private class JobTask(deadline: Long, private val job: Job) : Cancellable(deadline) {
        override val isActive: Boolean
            get() = super.isActive && job.isActive

        override fun cancel() {
            job.cancel()
        }
    }

    private class WeakTimeoutCoroutine<in T>(
        context: CoroutineContext,
        delegate: Continuation<T>,
        private val job: Job = Job(context[Job])
    ) : Continuation<T>, Job by job, CoroutineScope {
        override val context: CoroutineContext = context + job
        override val coroutineContext: CoroutineContext get() = context

        private val state = atomic<Continuation<T>?>(delegate)

        override fun resumeWith(result: Result<T>) {
            state.getAndUpdate {
                if (it == null) return
                null
            }?.let {
                it.resumeWith(result)
                job.cancel()
            }
        }

        fun tryComplete(): Boolean {
            state.update {
                if (it !is Continuation<*>) return false
                null
            }
            job.cancel()
            return true
        }
    }
}
