package org.jetbrains.ktor.netty

import io.netty.util.concurrent.*
import io.netty.util.concurrent.Future
import kotlinx.coroutines.experimental.*
import java.util.concurrent.*

suspend fun <T> Future<T>.suspendAwait(): T {
    if (isDone) return get()

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
            try {
                future.get()
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        } catch (t: Throwable) {
            continuation.resumeWithException(t)
            return
        }

        continuation.resume(value)
    }

    override fun dispose() {
        future.removeListener(this)
        if (continuation.isCancelled) future.cancel(false)
    }
}