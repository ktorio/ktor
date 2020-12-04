/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

@InternalAPI
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun <R, A>
    (suspend R.(A) -> Unit).startCoroutineUninterceptedOrReturn3(
    receiver: R,
    arg: A,
    continuation: Continuation<Unit>
): Any? {
    val block: suspend () -> Unit = { this(receiver, arg) }
    return block.startCoroutineUninterceptedOrReturn(continuation)
}

