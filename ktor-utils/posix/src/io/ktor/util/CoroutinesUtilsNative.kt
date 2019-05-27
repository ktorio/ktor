/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.util

import kotlin.coroutines.*

@InternalAPI
@Suppress("NOTHING_TO_INLINE")
internal actual inline fun <R, A>
    (suspend R.(A) -> Unit).startCoroutineUninterceptedOrReturn3(
    receiver: R,
    arg: A,
    continuation: Continuation<Unit>
): Any? {

    @Suppress("UNCHECKED_CAST")
    val function = (this as Function3<R, A, Continuation<Unit>, Any?>)
    return function.invoke(receiver, arg, continuation)
}

