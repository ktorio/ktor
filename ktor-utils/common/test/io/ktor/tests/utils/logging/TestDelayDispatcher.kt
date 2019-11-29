/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.jvm.*

@UseExperimental(InternalCoroutinesApi::class)
internal class TestDelayDispatcher(initialTime: Long) : CoroutineDispatcher(), Delay {
    private val scheduled = SortedQueue<Task>(compareBy { it.time })

    /**
     * Measure number of all queued and delayed tasks.
     */
    val queuedCount: Int
        get() {
            play()
            return scheduled.size
        }

    /**
     * Measure number of delayed tasks. Queued tasks are not counted.
     */
    val delayedCount: Int
        get() {
            play()
            return scheduled.count { it.time > 0L }
        }

    /**
     * Current clock value. Changing this value causes queue to be
     */
    var currentTimeMillis: Long = initialTime
        set(newTime) {
            require(newTime >= field) { "Time could be only stepped forward." }
            field = newTime
            play(newTime)
        }

    /**
     * Run loop over all scheduled tasks. Does only run delayed tasks that are ready (expired).
     */
    fun play() {
        play(currentTimeMillis)
    }

    private fun play(edge: Long) {
        val scheduled = scheduled
        while (true) {
            val task = scheduled.peek() ?: break
            val time = task.time
            if (time > edge) {
                break
            }

            scheduled.take()

            when (task) {
                is Task.Resume -> task.continuation.resume(Unit)
                is Task.Run -> task.runnable.run()
            }
        }
    }

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        val task = Task.Run(0, block)
        scheduled.add(task)
    }

    override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
        if (timeMillis <= 0) {
            continuation.resume(Unit)
            return
        }

        val resumeAt = currentTimeMillis + timeMillis
        val task = Task.Resume(resumeAt, continuation)
        continuation.invokeOnCancellation { scheduled.remove(task) }

        addTask(task)
    }

    override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
        if (timeMillis <= 0) {
            block.run()
            return DisposableHandle {}
        }

        val task = Task.Run(currentTimeMillis + timeMillis, block)
        addTask(task)

        return DisposableHandle {
            scheduled.remove(task)
        }
    }

    private fun addTask(task: Task) {
        scheduled.add(task)
//        scheduled.sortByDescending { it.time }
    }

    private sealed class Task(val time: Long) {
        class Resume(time: Long, val continuation: CancellableContinuation<Unit>) : Task(time)
        class Run(time: Long, val runnable: Runnable) : Task(time)
    }

    private class SortedQueue<T : Any>(private val comparator: Comparator<T>) {
        private val list = ArrayList<T>()

        @get:Synchronized
        val size: Int get() = list.size

        @Synchronized
        fun count(predicate: (T) -> Boolean): Int {
            return list.count(predicate)
        }

        @Synchronized
        fun add(element: T) {
            val list = list
            var left = 0
            var right = list.size
            while (left < right) {
                val mid = (left + right) / 2
                val probe = list[mid]
                val comparison = comparator.compare(probe, element)

                when {
                    comparison == 0 -> {
                        left = mid
                        right = mid
                    }
                    comparison > 0 -> {
                        right = mid
                    }
                    left == mid -> {
                        left++
                    }
                    else -> {
                        left = mid
                    }
                }
            }

            while (left < list.lastIndex) {
                val next = left + 1
                if (comparator.compare(list[next], element) != 0) {
                    break
                }
                left = next
            }

            list.add(left, element)
        }

        @Synchronized
        fun take(): T {
            return list.removeAt(0)
        }

        @Synchronized
        fun peek(): T? {
            if (list.isEmpty()) return null
            return list[0]
        }

        @Synchronized
        fun remove(element: T) {
            list.remove(element)
        }
    }
}
