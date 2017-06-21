package org.jetbrains.ktor.netty

import io.netty.util.concurrent.*
import io.netty.util.concurrent.Future
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

suspend fun <T> Future<T>.suspendAwait(): T {
    if (isDone) return try { get() } catch (t: Throwable) { throw t.unwrap() }

    return suspendCancellableCoroutine { continuation ->
        addListener(CoroutineListener(this, continuation))
    }
}

private class CoroutineListener<T, F : Future<T>>(private val future: F, private val continuation: CancellableContinuation<T>) : GenericFutureListener<F>, DisposableHandle {
    init {
        continuation.disposeOnCompletion(this)
    }

    override fun operationComplete(future: F) {
        val value = try {
            future.get()
        } catch (t: Throwable) {
            continuation.resumeWithException(t.unwrap())
            return
        }

        continuation.resume(value)
    }

    override fun dispose() {
        future.removeListener(this)
        if (continuation.isCancelled) future.cancel(false)
    }
}

private tailrec fun Throwable.unwrap(): Throwable = if (this is ExecutionException && cause != null) cause!!.unwrap() else this