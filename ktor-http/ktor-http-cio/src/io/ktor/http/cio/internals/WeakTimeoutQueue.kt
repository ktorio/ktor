package io.ktor.http.cio.internals

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.internal.*
import java.time.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*
import kotlin.jvm.*

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
class WeakTimeoutQueue(
    private val timeoutMillis: Long,
    private val clock: Clock = Clock.systemUTC(),
    private val exceptionFactory: () -> Exception = { TimeoutCancellationException("Timeout of $timeoutMillis ms exceeded") }
) {
    private val head = LockFreeLinkedListHead()

    @Volatile
    private var cancelled = false

    fun register(r: Job): DisposableHandle {
        val now = clock.millis()
        val head = head
        if (cancelled) throw cancellationException()

        val cancellable = JobTask(now + timeoutMillis, r)
        head.addLast(cancellable)

        process(now, head, cancelled)
        if (cancelled) {
            val e = cancellationException()
            cancellable.cancel(e)
            throw e
        }

        return cancellable
    }

    fun cancel() {
        cancelled = true
        process()
    }

    fun process() {
        process(clock.millis(), head, cancelled)
    }

    suspend fun <T> withTimeout(block: suspend CoroutineScope.() -> T): T {
        return suspendCoroutineOrReturn { c ->
            val wrapped = WeakTimeoutCoroutine(c.context, c)
            val handle = register(wrapped)

//            wrapped.initParentJob(c.context[Job])
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