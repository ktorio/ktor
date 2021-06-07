/*
* Copyright 2014-2021 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
*/

package io.ktor.http.content

import kotlinx.coroutines.*
import java.lang.reflect.*

private val isParkingAllowedFunction: Method? by lazy {
    try {
        Class.forName("io.ktor.utils.io.jvm.javaio.PollersKt")
            .getMethod("isParkingAllowed")
    } catch (cause: Throwable) {
        null
    }
}

/**
 * Execute [block] function either directly or redispatch on [Dispatchers.IO].
 * Redispatch is usually required when running on a thread that does not allow blocking
 * because it handles an event loop and/or epoll/kqueue/select operations.
 * Note that coroutines event loop thread usually can handle some blocking operations
 * so no need to redispatch.
 */
internal suspend fun withBlocking(block: suspend () -> Unit) {
    if (safeToRunInPlace()) {
        return block()
    }

    return withBlockingAndRedispatch(block)
}

private fun safeToRunInPlace(): Boolean {
    val isParkingAllowed = isParkingAllowedFunction
    return isParkingAllowed != null && try {
        isParkingAllowed.invoke(null) == true
    } catch (cause: Throwable) {
        false
    }
}

private suspend fun withBlockingAndRedispatch(block: suspend () -> Unit) {
    withContext(Dispatchers.IO) {
        block()
    }
}
