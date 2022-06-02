/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.tests.http.cio

import kotlinx.coroutines.*
import kotlinx.coroutines.intrinsics.*
import kotlin.coroutines.*
import kotlin.test.*

@OptIn(InternalCoroutinesApi::class)
internal fun test(block: suspend () -> Unit) {
    var failure: Throwable? = null
    var completed = false
    val cont = Continuation<Unit>(EmptyCoroutineContext) {
        completed = true
        failure = it.exceptionOrNull()
    }

    block.startCoroutineCancellable(cont)
    if (!completed) {
        fail("Suspended unexpectedly.")
    }

    failure?.let { throw it }
}
