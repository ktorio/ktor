/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.network.util

import kotlinx.coroutines.*
import kotlin.coroutines.*

private class TrackingContinuation<in T>(private val delegate: CancellableContinuation<T>) : CancellableContinuation<T> by delegate {
    private val suspensionPoint = Exception("Suspension point").apply {
        fillInStackTrace()
    }

    override fun resumeWith(result: Result<T>) {
        if (result.isSuccess) delegate.resumeWith(result)
        else {
            suspensionPoint.initCause(result.exceptionOrNull())
            delegate.resumeWithException(suspensionPoint)
        }
    }

    @OptIn(InternalCoroutinesApi::class)
    override fun tryResumeWithException(exception: Throwable): Any? {
        val token = delegate.tryResumeWithException(suspensionPoint)
        if (token != null) {
            suspensionPoint.initCause(exception)
        }
        return token
    }
}

//internal fun <T> CancellableContinuation<T>.tracked(): CancellableContinuation<T> = TrackingContinuation(this)
