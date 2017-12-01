package io.ktor.cio

import kotlinx.coroutines.experimental.*
import java.util.concurrent.*
import kotlin.coroutines.experimental.*
import kotlin.coroutines.experimental.intrinsics.*

fun <T> runSync(block: suspend () -> T): T {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    if (result == COROUTINE_SUSPENDED) {
        throw IllegalStateException("function passed to runSync suspended")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

fun CoroutineContext.executor(): Executor = Executor {
    launch(this) { it.run() }
}

object NoopContinuation : Continuation<Any?> {
    override val context: CoroutineContext = EmptyCoroutineContext
    override fun resume(value: Any?) {}
    override fun resumeWithException(exception: Throwable) {}
}
