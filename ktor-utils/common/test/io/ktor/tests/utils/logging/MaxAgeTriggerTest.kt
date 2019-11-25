/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.tests.utils.logging

import io.ktor.util.date.*
import io.ktor.util.logging.rolling.*
import kotlinx.coroutines.*
import kotlin.coroutines.*
import kotlin.test.*

class MaxAgeTriggerTest : CoroutineScope {
    private val maxAge = 100000L
    private var triggered = 0

    private val job = Job()
    private val dispatcher = TestDispatcher(0L)
    private val fileSystem = TestFileSystem()
    private val trigger = Trigger.MaxAge(maxAge, fileSystem, FilePathPattern("file-%i"), {
        triggered++
    }, { now })

    override val coroutineContext: CoroutineContext get() = job + dispatcher

    private var now: GMTDate
        get() = GMTDate(dispatcher.currentTime)
        set(value) {
            dispatcher.currentTime = value.timestamp
        }

    @BeforeTest
    fun setup() {
        trigger.setup(this)
        assertFalse(trigger.check())
    }

    @AfterTest
    fun cleanup() {
        job.cancel("Test completed.")
    }

    @Test
    fun smokeTest() {
        fileSystem.addFile("file-1")
        val file = fileSystem.allFiles.single { it.path == "file-1" } as TestFileSystem.Entry.File

        file.lastModified = now
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        file.lastModified = now - 10
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        assertEquals(1, dispatcher.delayedCount)

        file.lastModified = now - maxAge - 1
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(1, triggered)
    }

    @Test
    fun testSchedule() {
        fileSystem.addFile("file-1")
        val file = fileSystem.allFiles.single { it.path == "file-1" } as TestFileSystem.Entry.File

        file.lastModified = now
        fileSystem.notify { it.fileUpdated("file-1") }
        assertEquals(0, triggered)

        assertEquals(1, dispatcher.delayedCount)
    }

    @UseExperimental(InternalCoroutinesApi::class)
    private class TestDispatcher(initialTime: Long) : CoroutineDispatcher(), Delay {
        private val scheduled = ArrayList<Task>()

        val scheduledCount: Int
            get() {
                play()
                return scheduled.size
            }

        val delayedCount: Int
            get() {
                play()
                return scheduled.count { it.time > 0L }
            }

        var currentTime: Long = initialTime
            set(newTime) {
                field = newTime
                play(newTime)
            }

        fun play() {
            play(currentTime)
        }

        private fun play(edge: Long) {
            val scheduled = scheduled
            while (scheduled.isNotEmpty()) {
                val task = scheduled.last()
                val time = task.time
                if (time > edge) {
                    break
                }

                scheduled.removeAt(scheduled.lastIndex)

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

            val resumeAt = currentTime + timeMillis
            val task = Task.Resume(resumeAt, continuation)
            continuation.invokeOnCancellation { scheduled.remove(task) }

            addTask(task)
        }

        override fun invokeOnTimeout(timeMillis: Long, block: Runnable): DisposableHandle {
            if (timeMillis <= 0) {
                block.run()
                return DisposableHandle {}
            }

            val task = Task.Run(currentTime + timeMillis, block)
            addTask(task)

            return DisposableHandle {
                scheduled.remove(task)
            }
        }

        private fun addTask(task: Task) {
            scheduled.add(task)
            scheduled.sortByDescending { it.time }
        }

        private sealed class Task(val time: Long) {
            class Resume(time: Long, val continuation: CancellableContinuation<Unit>) : Task(time)
            class Run(time: Long, val runnable: Runnable) : Task(time)
        }
    }
}
