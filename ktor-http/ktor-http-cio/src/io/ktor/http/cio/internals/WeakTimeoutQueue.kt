package io.ktor.http.cio.internals

import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.*
import java.time.*
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
@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
class WeakTimeoutQueue(
    private val timeoutMillis: Long,
    private val clock: Clock = Clock.systemUTC(),
    private val exceptionFactory: () -> Exception = { TimeoutCancellationException(timeoutMillis) }
) {
    private val head = LockFreeLinkedListHead()

    @kotlin.jvm.Volatile
    private var cancelled = false

    /**
     * Register [job] in this queue. It will be cancelled if doesn't complete in time.
     */
    fun register(job: Job): DisposableHandle {
        val now = clock.millis()
        val head = head
        if (cancelled) throw cancellationException()

        val cancellable = JobTask(now + timeoutMillis, job)
        head.addLast(cancellable)

        process(now, head, cancelled)
        if (cancelled) {
            val e = cancellationException()
            cancellable.cancel(e)
            throw e
        }

        return cancellable
    }

    /**
     * Cancel all registered timeouts
     */
    fun cancel() {
        cancelled = true
        process()
    }

    /**
     * Process and cancel all jobs that are timed out
     */
    fun process() {
        process(clock.millis(), head, cancelled)
    }

    /**
     * Execute [block] and cancel if doesn't complete it time.
     */
    suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T {
        return suspendCoroutineUninterceptedOrReturn { rawContinuation ->
            val continuation = rawContinuation.intercepted()
            val wrapped = WeakTimeoutCoroutine(continuation.context, continuation)
            val handle = register(wrapped)
            wrapped.disposeOnCompletion(handle)

            val result = try {
                if (wrapped.isCancelled) throw wrapped.getCancellationException()
                block.startCoroutineUninterceptedOrReturn(receiver = wrapped, completion = wrapped)
            } catch (t: Throwable) {
                CompletedExceptionally(t)
            }

            unwrapResult(wrapped, handle, result)
        }
    }

    private fun unwrapResult(c: WeakTimeoutCoroutine<*>, handle: DisposableHandle, result: Any?): Any? {
        val suspended = COROUTINE_SUSPENDED
        return when {
            result === suspended -> result
            c.isCompleted -> suspended
            result is CompletedExceptionally -> {
                handle.dispose()
                throw result.cause
            }
            else -> {
                handle.dispose()
                result
            }
        }
    }

    private fun process(now: Long, head: LockFreeLinkedListHead, cancelled: Boolean) {
        var e: Throwable? = null

        while (true) {
            val p = head.next as? Cancellable ?: break
            if (!cancelled && p.deadline > now) break

            if (p.isActive && p.remove()) {
                if (e == null) e = if (cancelled) cancellationException() else exceptionFactory()
                p.cancel(e)
            }
        }
    }

    private fun cancellationException() = CancellationException("Timeout queue has been cancelled")

    @UseExperimental(InternalCoroutinesApi::class)
    private abstract class Cancellable(val deadline: Long) : LockFreeLinkedListNode(), DisposableHandle {
        open val isActive: Boolean
            get() = !isRemoved

        abstract fun cancel(t: Throwable)

        override fun dispose() {
            remove()
        }
    }

    private class JobTask(deadline: Long, private val job: Job) : Cancellable(deadline) {
        override val isActive: Boolean
            get() = super.isActive && job.isActive

        override fun cancel(t: Throwable) {
            job.cancel(t)
        }
    }

    @UseExperimental(InternalCoroutinesApi::class)
    private class WeakTimeoutCoroutine<in T>(context: CoroutineContext, val delegate: Continuation<T>) :
        AbstractCoroutine<T>(context, true), Continuation<T> {
        override fun onCompleted(value: T) {
            delegate.resume(value)
        }

        override fun onCompletedExceptionally(exception: Throwable) {
            delegate.resumeWithException(exception)
        }
    }
}

@InternalAPI
class TimeoutCancellationException(message: String) : CancellationException(message) {
    constructor(timeoutMillis: Long) : this("Timeout of $timeoutMillis ms exceeded")
}
