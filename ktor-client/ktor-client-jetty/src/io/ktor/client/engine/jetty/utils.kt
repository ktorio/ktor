package io.ktor.client.engine.jetty

import org.eclipse.jetty.util.*
import kotlin.coroutines.experimental.*

internal suspend fun <R> withPromise(block: (Promise<R>) -> Unit): R {
    return suspendCoroutine { continuation ->
        block(PromiseContinuation(continuation))
    }
}

internal suspend fun <R> withCallback(block: (Callback) -> Unit) {
    return suspendCoroutine { continuation ->
        block(CallbackContinuation(continuation))
    }
}

internal class PromiseContinuation<R>(val continuation: Continuation<R>) : Promise<R> {
    override fun failed(x: Throwable) {
        continuation.resumeWithException(x)
    }

    override fun succeeded(result: R) {
        continuation.resume(result)
    }
}

internal class CallbackContinuation(val continuation: Continuation<Unit>) : Callback {
    override fun succeeded() {
        continuation.resume(Unit)
    }

    override fun failed(x: Throwable) {
        continuation.resumeWithException(x)
    }
}