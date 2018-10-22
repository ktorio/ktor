package io.ktor.client.engine.curl.temporary

import platform.posix.usleep
import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

class EventKey

val anEventLoop = AnEventLoop()

// This is a smoke and mirrors quickhack just to have a dispatcher
// capable of calling tasks periodically.
// We should be able to ask coroutine dispatcher for that, I suppose.
// This doesn't take into account delay() or any other timing for now,
// just reschedules periodic tasks constantly.
// Need a Delay implementation?
// Or should we just use native event loops (gtk, windows, gcd etc) timer capabilities?
class AnEventLoop : CoroutineDispatcher() {
    private var queue = mutableListOf<() -> Unit>()
    private val periodic = LinkedHashMap<EventKey, () -> Unit>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        submit { block.run() }
    }

    fun submit(task: () -> Unit) {
        queue.add(task)
    }

    fun subscribePeriodic(task: () -> Unit): EventKey {
        val key = EventKey()
        periodic.put(key, task)
        return key
    }

    fun removePeriodic(key: EventKey) {
        periodic.remove(key)
    }

    fun run(task: () -> Unit) {
        submit(task)
        loop()
    }

    fun loop() {
        while (true) {
            if (queue.size > 0) {
                val task = queue.first()
                queue.removeAt(0)
                task()
            }

            periodic.forEach { (key, value) ->
                value()
            }

            if (queue.size == 0 && periodic.size == 0) return
        }
    }
}
