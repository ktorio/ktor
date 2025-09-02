/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.webrtc

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlin.coroutines.CoroutineContext

/**
 * Default coroutine exception handler that logs all errors except of [CancellationException].
 */
public class DefaultExceptionHandler(private val name: String) : CoroutineExceptionHandler {
    override val key: CoroutineContext.Key<*> = CoroutineExceptionHandler.Key

    override fun handleException(context: CoroutineContext, exception: Throwable) {
        if (exception is CancellationException) {
            return
        }
        val coroutineName = context[CoroutineName] ?: context.toString()
        println(
            "[ERROR] ($name): Unhandled exception caught for $coroutineName. Cause: ${exception.stackTraceToString()}"
        )
    }
}
