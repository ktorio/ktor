/*
 * Copyright 2014-2022 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.client.engine.winhttp.internal

import io.ktor.utils.io.core.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import platform.winhttp.*
import kotlin.coroutines.*

internal suspend inline fun <T> Closeable.closeableCoroutine(
    state: WinHttpConnect,
    errorMessage: String,
    crossinline block: (CancellableContinuation<T>) -> Unit
): T = suspendCancellableCoroutine { continuation ->
    if (!continuation.isActive) {
        close()
        return@suspendCancellableCoroutine
    }

    continuation.invokeOnCancellation {
        close()
    }

    state.handlers[WinHttpCallbackStatus.RequestError.value] = { statusInfo, _ ->
        val result = statusInfo!!.reinterpret<WINHTTP_ASYNC_RESULT>().pointed
        continuation.resumeWithException(getWinHttpException(errorMessage, result.dwError))
    }

    state.handlers[WinHttpCallbackStatus.SecureFailure.value] = { statusInfo, _ ->
        val securityCode = statusInfo!!.reinterpret<UIntVar>().pointed.value
        continuation.resumeWithException(getWinHttpException(errorMessage, securityCode))
    }

    try {
        block(continuation)
    } catch (cause: Throwable) {
        continuation.resumeWithException(cause)
    }
}
