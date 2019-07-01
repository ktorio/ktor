package io.ktor.utils.io

import java.util.concurrent.atomic.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*
import kotlin.jvm.*

internal actual class Condition actual constructor(val predicate: () -> Boolean) {
    @Volatile
    private var cond: Continuation<Unit>? = null

    actual fun check(): Boolean {
        return predicate()
    }

    actual fun signal() {
        val cond = cond
        if (cond != null && predicate()) {
            if (updater.compareAndSet(this, cond, null)) {
                cond.intercepted().resume(Unit)
            }
        }
    }

    actual suspend inline fun await(crossinline block: () -> Unit) {
        if (predicate()) return

        return suspendCoroutineUninterceptedOrReturn { c ->
            if (!updater.compareAndSet(this, null, c)) throw IllegalStateException()
            if (predicate() && updater.compareAndSet(this, c, null)) return@suspendCoroutineUninterceptedOrReturn Unit //c.resume(Unit)
            block()
            COROUTINE_SUSPENDED
        }
    }

    actual suspend inline fun await() {
        if (predicate()) return

        return suspendCoroutineUninterceptedOrReturn { c ->
            if (!updater.compareAndSet(this, null, c)) throw IllegalStateException()
            if (predicate() && updater.compareAndSet(this, c, null)) return@suspendCoroutineUninterceptedOrReturn Unit // c.resume(Unit)
            COROUTINE_SUSPENDED
        }
    }

    override fun toString(): String {
        return "Condition(cond=$cond)"
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private val updater = AtomicReferenceFieldUpdater.newUpdater<Condition, Continuation<*>>(Condition::class.java,
            Continuation::class.java, "cond") as AtomicReferenceFieldUpdater<Condition, Continuation<Unit>?>
    }
}
