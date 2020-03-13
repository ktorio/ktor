@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import kotlin.coroutines.*
import platform.Foundation.*

/**
 * Amount of time any task is processed and can't be rescheduled.
 */
private const val TIME_QUANTUM = 0.01

/**
 * Test runner for native suspend tests.
 */
actual fun testSuspend(
    context: CoroutineContext,
    block: suspend CoroutineScope.() -> Unit
): Unit = runBlocking {
    val loop = coroutineContext[ContinuationInterceptor] as EventLoop

    val task = launch { block() }
    while (!task.isCompleted) {
        val date = NSDate().addTimeInterval(TIME_QUANTUM) as NSDate
        NSRunLoop.mainRunLoop.runUntilDate(date)

        loop.processNextEvent()
    }
}
