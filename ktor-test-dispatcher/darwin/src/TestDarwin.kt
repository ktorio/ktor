/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")

package io.ktor.test.dispatcher

import kotlinx.coroutines.*
import platform.Foundation.*
import platform.posix.*
import kotlin.coroutines.*
import kotlin.native.concurrent.*
import kotlin.system.*

/**
 * Amount of time any task is processed and can't be rescheduled.
 */
private const val TIME_QUANTUM = 0.01

/**
 * Test runner for native suspend tests.
 */
public actual fun testSuspend(
    context: CoroutineContext,
    dispatchTimeoutMs: Long,
    testBody: suspend TestScope.() -> Unit
): TestResult {
    executeInWorker(dispatchTimeoutMs) {
        runBlocking {
            val loop = ThreadLocalEventLoop.currentOrNull()!!

            val task = launch { TestScope().testBody() }
            while (!task.isCompleted) {
                val date = NSDate().addTimeInterval(TIME_QUANTUM) as NSDate
                NSRunLoop.mainRunLoop.runUntilDate(date)

                loop.processNextEvent()
            }
        }
    }
}

@Suppress("ACTUAL_WITHOUT_EXPECT")
public actual typealias TestResult = Unit
