package io.ktor.utils.io

import kotlin.coroutines.*

internal actual class Condition actual constructor(val predicate: () -> Boolean) {
    private var cont: Continuation<Unit>? = null

    actual fun check(): Boolean {
        return predicate()
    }

    actual fun signal() {
        val cont = cont
        if (cont != null && predicate()) {
            this.cont = null
            cont.resume(Unit)
        }
    }

    actual suspend fun await(block: () -> Unit) {
        if (predicate()) return

        return suspendCoroutine { c ->
            cont = c
            block()
        }
    }
    actual suspend fun await() {
        if (predicate()) return

        return suspendCoroutine { c ->
            cont = c
        }
    }
}
