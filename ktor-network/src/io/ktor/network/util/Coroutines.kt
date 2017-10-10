package io.ktor.network.util

import kotlinx.coroutines.experimental.*

private class TrackingContinuation<in T>(private val delegate: CancellableContinuation<T>) : CancellableContinuation<T> by delegate {
    private val suspensionPoint = Exception().apply {
        fillInStackTrace()
    }

    override fun resumeWithException(exception: Throwable) {
        suspensionPoint.addSuppressed(exception)
        delegate.resumeWithException(suspensionPoint)
    }
}

internal fun <T> CancellableContinuation<T>.tracked(): CancellableContinuation<T> = TrackingContinuation(this)
