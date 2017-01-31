package org.jetbrains.ktor.util

import kotlinx.coroutines.experimental.intrinsics.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

fun <T> runSync(block: suspend () -> T): T {
    val result = block.startCoroutineOrReturn(NoopContinuation)
    if (result == COROUTINE_SUSPENDED) {
        throw IllegalStateException("function passed to runSync suspended")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

object NoopContinuation : Continuation<Any?> {
    override val context: CoroutineContext = EmptyCoroutineContext
    override fun resume(value: Any?) {}
    override fun resumeWithException(exception: Throwable) {}
}
