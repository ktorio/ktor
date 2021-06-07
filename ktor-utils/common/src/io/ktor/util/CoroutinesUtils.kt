/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.util

import kotlinx.coroutines.*
import kotlin.coroutines.*

/**
 * Print [Job] children tree.
 */
@InternalAPI
public fun Job.printDebugTree(offset: Int = 0) {
    println(" ".repeat(offset) + this)

    children.forEach {
        it.printDebugTree(offset + 2)
    }

    if (offset == 0) println()
}

@InternalAPI
@Suppress("NOTHING_TO_INLINE")
internal expect inline fun <R, A> (suspend R.(A) -> Unit).startCoroutineUninterceptedOrReturn3(
    receiver: R,
    arg: A,
    continuation: Continuation<Unit>
): Any?

/**
 * Supervisor with empty coroutine exception handler ignoring all exceptions.
 */
@InternalAPI
public fun SilentSupervisor(parent: Job? = null): CoroutineContext =
    SupervisorJob(parent) + CoroutineExceptionHandler { _, _ -> }
