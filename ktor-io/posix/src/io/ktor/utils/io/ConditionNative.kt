package io.ktor.utils.io

import io.ktor.utils.io.concurrent.*
import kotlinx.coroutines.*
import kotlin.coroutines.*

internal actual class Condition actual constructor(val predicate: () -> Boolean) {
    private var continuation: Continuation<Unit>? by shared(null)

    actual fun check(): Boolean {
        return predicate()
    }

    actual fun signal() {
        val reference = continuation
        if (reference != null && predicate()) {
            continuation = null
            reference.resume(Unit)
        }
    }

    actual suspend fun await(block: () -> Unit) {
        if (predicate()) return

        return suspendCancellableCoroutine { current ->
            continuation = current
            block()
        }
    }

    actual suspend fun await() {
        if (predicate()) return

        return suspendCancellableCoroutine { current ->
            continuation = current
        }
    }
}
