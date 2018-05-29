package io.ktor.network.util

import kotlinx.coroutines.experimental.*

private class TrackingContinuation<in T>(private val delegate: CancellableContinuation<T>) : CancellableContinuation<T> by delegate {
    private val suspensionPoint = Exception("Suspension point").apply {
        fillInStackTrace()
    }

    override fun resumeWithException(exception: Throwable) {
        suspensionPoint.initCause(exception)
        delegate.resumeWithException(suspensionPoint)
    }

    override fun tryResumeWithException(exception: Throwable): Any? {
        val token = delegate.tryResumeWithException(suspensionPoint)
        if (token != null) {
            suspensionPoint.initCause(exception)
        }
        return token
    }
}

internal fun <T> CancellableContinuation<T>.tracked(): CancellableContinuation<T> = TrackingContinuation(this)
