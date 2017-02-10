package org.jetbrains.ktor.util

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import kotlin.coroutines.experimental.*

class AsyncCountDownLatch(count: Int) {
    @Volatile
    var value = count
        private set

    private val awaiters = LinkedBlockingQueue<Task>()

    suspend fun countDown() {
        while (true) {
            val current = value
            if (current == 0)
                return

            if (ValueUpdater.compareAndSet(this, current, current - 1)) {
                if (current > 1) return
                break
            }
        }

        while (true) {
            val t = awaiters.poll() ?: break

            if (t.execute()) {
                t.continuation.resume(Unit)
            }
        }
    }

    suspend fun await() {
        if (value == 0)
            return

        suspendCoroutine<Unit> { cont ->
            val task = Task(cont)
            awaiters.put(task)

            if (value == 0 && task.cancel()) {
                awaiters.remove(task)
                cont.resume(Unit)
            }
        }
    }

    override fun toString(): String {
        return "CountDownLatch($value, ${awaiters.size} pending)"
    }

    private class Task(val continuation: Continuation<Unit>) {
        val state = AtomicReference(State.PENDING)

        fun execute(): Boolean = state.compareAndSet(State.PENDING, State.EXECUTED)

        fun cancel(): Boolean = state.compareAndSet(State.PENDING, State.CANCELLED)

        enum class State {
            PENDING,
            CANCELLED,
            EXECUTED
        }
    }

    companion object {
        private val ValueUpdater = AtomicIntegerFieldUpdater.newUpdater(AsyncCountDownLatch::class.java, "value")!!
    }
}