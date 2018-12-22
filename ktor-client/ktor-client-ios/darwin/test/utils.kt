package io.ktor.client.test

import kotlinx.coroutines.*
import platform.Foundation.*
import kotlin.coroutines.*

internal class TestDispatcher : CoroutineDispatcher() {
    private val tasks = mutableSetOf<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks += block
    }

    fun schedule(block: suspend () -> Unit, onDone: (Throwable?) -> Unit) {
        block.startCoroutine(TestContinuation(this, onDone))
    }

    fun isEmpty() = tasks.isEmpty()

    fun runSingleTask() {
        if (tasks.isEmpty()) {
            help()
            return
        }

        val task = tasks.iterator().next()
        try {
            task.run()
        } catch (cause: Throwable) {
            cause.printStackTrace()
        } finally {
            tasks.remove(task)
        }
    }

    private fun help() {
        val date = NSDate().addTimeInterval(1.0) as NSDate
        NSRunLoop.mainRunLoop.runUntilDate(date)
    }
}

internal class TestContinuation(
    override val context: CoroutineContext,
    private val onDone: (Throwable?) -> Unit
) : Continuation<Unit> {
    override fun resumeWith(result: Result<Unit>) {
        onDone(result.exceptionOrNull())
    }
}

internal fun help(block: suspend CoroutineDispatcher.() -> Unit) {
    val dispatcher = TestDispatcher()
    var done = false
    var cause: Throwable? = null
    dispatcher.schedule({ dispatcher.block() }) {
        done = true
        cause = it
    }

    while (!done) {
        dispatcher.runSingleTask()
        cause?.let {
            it.printStackTrace()
            cause = null
        }
    }

    while (!dispatcher.isEmpty()) {
        dispatcher.runSingleTask()
        cause?.let {
            it.printStackTrace()
            cause = null
        }
    }
}
