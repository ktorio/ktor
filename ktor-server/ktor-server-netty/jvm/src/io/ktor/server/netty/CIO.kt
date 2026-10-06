/*
 * Copyright 2014-2024 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.netty

import io.ktor.util.cio.*
import io.ktor.util.logging.*
import io.netty.channel.*
import io.netty.util.concurrent.*
import io.netty.util.concurrent.Future
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*
import kotlin.coroutines.*

private val LOG = KtorSimpleLogger("io.ktor.server.netty.CIO")

@Suppress("IMPLICIT_NOTHING_AS_TYPE_PARAMETER")
private val identityErrorHandler = { t: Throwable, c: Continuation<*> ->
    c.resumeWithException(t)
}

/**
 * Suspend until the future completion.
 * Resumes with the same exception if the future completes exceptionally
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.suspendAwait)
 */
public suspend fun <T> Future<T>.suspendAwait(): T {
    return suspendAwait(identityErrorHandler)
}

@Suppress("IMPLICIT_NOTHING_AS_TYPE_PARAMETER")
private val wrappingErrorHandler = { t: Throwable, c: Continuation<*> ->
    if (t is IOException) {
        c.resumeWithException(ChannelWriteException("Write operation future failed", t))
    } else {
        c.resumeWithException(t)
    }
}

/**
 * Suspend until the future completion.
 * Wraps futures completion exceptions into [ChannelWriteException]
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.suspendWriteAwait)
 */
public suspend fun <T> Future<T>.suspendWriteAwait(): T {
    return suspendAwait(wrappingErrorHandler)
}

/**
 * Suspend until the future completion handling exception from the future using [exception] function
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.netty.suspendAwait)
 */
public suspend fun <T> Future<T>.suspendAwait(exception: (Throwable, Continuation<T>) -> Unit): T {
    @Suppress("BlockingMethodInNonBlockingContext")
    if (isDone) {
        try {
            return get()
        } catch (t: Throwable) {
            throw t.unwrap()
        }
    }

    return suspendCancellableCoroutine { continuation ->
        addListener(CoroutineListener(this, continuation, exception))
    }
}

/**
 * The netty executor handles async execution with thread affinity, so we use it
 * by default for the user context dispatcher.  There are some scenarios where the
 * underlying netty channel is closed prematurely, in which case we fallback to the
 * caller thread.
 *
 * Also implements [Delay] so `delay()` and timeout scheduling (`withTimeout`) are scheduled
 * directly on the call's own Netty [EventExecutor] (a [java.util.concurrent.ScheduledExecutorService])
 * instead of kotlinx.coroutines' single shared `DefaultExecutor` thread.
 */
@OptIn(InternalCoroutinesApi::class, ExperimentalCoroutinesApi::class)
internal object NettyDispatcher : CoroutineDispatcher(), Delay {
    private inline fun <E> execute(
        context: CoroutineContext,
        action: EventExecutor.() -> E,
        fallback: () -> E,
    ): E {
        try {
            val current = context[CurrentContextKey]
            checkNotNull(current) { "NettyDispatcher context is missing from the coroutine context" }
            return if (!current.executor.isShuttingDown) {
                current.executor.action()
            } else {
                fallback()
            }
        } catch (_: RejectedExecutionException) {
            return fallback()
        } catch (cause: Throwable) {
            LOG.error("Failed to execute task", cause)
            throw cause
        }
    }

    // Used when the call's own Netty executor rejects scheduling (during shutdown, for example)
    private val fallbackScheduler: ScheduledExecutorService by lazy {
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ktor-netty-dispatcher-fallback").apply { isDaemon = true }
        }
    }

    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return context[CurrentContextKey]?.executor?.inEventLoop() != true
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        execute(
            context = context,
            action = { execute(block) },
            fallback = { Dispatchers.IO.dispatch(context, block) }
        )
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        val future = execute(
            context = continuation.context,
            action = {
                schedule(
                    { with(continuation) { resumeUndispatched(Unit) } },
                    timeMillis,
                    TimeUnit.MILLISECONDS
                )
            },
            fallback = {
                fallbackScheduler.schedule(
                    { continuation.resume(Unit) },
                    timeMillis,
                    TimeUnit.MILLISECONDS
                )
            }
        )
        continuation.invokeOnCancellation { future.cancel(false) }
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle {
        val future = execute(
            context = context,
            action = { schedule(block, timeMillis, TimeUnit.MILLISECONDS) },
            fallback = { fallbackScheduler.schedule(block, timeMillis, TimeUnit.MILLISECONDS) }
        )
        return DisposableHandle { future.cancel(false) }
    }

    /**
     * Carries the Netty channel context for the current call along with the executor that should be used
     * to dispatch coroutine continuations. When [executor] is not provided, the channel's own
     * event-loop executor is used (which runs I/O work).
     */
    class CurrentContext(
        val context: ChannelHandlerContext,
        val executor: EventExecutor = context.executor()
    ) : AbstractCoroutineContextElement(CurrentContextKey)

    object CurrentContextKey : CoroutineContext.Key<CurrentContext>
}

private class CoroutineListener<T, F : Future<T>>(
    private val future: F,
    private val continuation: CancellableContinuation<T>,
    private val exception: (Throwable, Continuation<T>) -> Unit
) : GenericFutureListener<F>, CompletionHandler {
    init {
        continuation.invokeOnCancellation(this)
    }

    override fun operationComplete(future: F) {
        val value = try {
            future.get()
        } catch (t: Throwable) {
            exception(t.unwrap(), continuation)
            return
        }

        continuation.resume(value)
    }

    override fun invoke(p1: Throwable?) {
        future.removeListener(this)
        if (continuation.isCancelled) future.cancel(false)
    }
}

private tailrec fun Throwable.unwrap(): Throwable =
    if (this is ExecutionException && cause != null) cause!!.unwrap() else this
