package io.ktor.server.netty

import io.ktor.util.cio.*
import io.netty.channel.*
import io.netty.util.concurrent.*
import io.netty.util.concurrent.Future
import kotlinx.coroutines.*
import java.io.*
import java.util.concurrent.*
import kotlin.coroutines.*

private val identityErrorHandler = { t: Throwable, c: Continuation<*> ->
    c.resumeWithException(t)
}

/**
 * Suspend until the future completion.
 * Resumes with the same exception if the future completes exceptionally
 */
suspend fun <T> Future<T>.suspendAwait(): T {
    return suspendAwait(identityErrorHandler)
}

private val wrappingErrorHandler = { t: Throwable, c: Continuation<*> ->
    if (t is IOException) c.resumeWithException(ChannelWriteException("Write operation future failed", t))
    else c.resumeWithException(t)
}

/**
 * Suspend until the future completion.
 * Wraps futures completion exceptions into [ChannelWriteException]
 */
suspend fun <T> Future<T>.suspendWriteAwait(): T {
    return suspendAwait(wrappingErrorHandler)
}

/**
 * Suspend until the future completion handling exception from the future using [exception] function
 */
suspend fun <T> Future<T>.suspendAwait(exception: (Throwable, Continuation<T>) -> Unit): T {
    @Suppress("BlockingMethodInNonBlockingContext")
    if (isDone) return try { get() } catch (t: Throwable) { throw t.unwrap() }

    return suspendCancellableCoroutine { continuation ->
        addListener(CoroutineListener(this, continuation, exception))
    }
}

internal object NettyDispatcher : CoroutineDispatcher() {
    override fun isDispatchNeeded(context: CoroutineContext): Boolean {
        return !context[CurrentContextKey]!!.context.executor().inEventLoop()
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val nettyContext = context[CurrentContextKey]!!.context
        nettyContext.executor().execute(block)
    }

    class CurrentContext(val context: ChannelHandlerContext) : AbstractCoroutineContextElement(CurrentContextKey)
    object CurrentContextKey : CoroutineContext.Key<CurrentContext>
}

private class CoroutineListener<T, F : Future<T>>(private val future: F,
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

private tailrec fun Throwable.unwrap(): Throwable = if (this is ExecutionException && cause != null) cause!!.unwrap() else this
