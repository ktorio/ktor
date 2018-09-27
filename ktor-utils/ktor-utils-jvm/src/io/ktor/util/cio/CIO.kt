package io.ktor.util.cio

import kotlinx.coroutines.*
import java.lang.Runnable
import java.util.concurrent.*
import kotlin.coroutines.*
import kotlin.coroutines.intrinsics.*

fun <T> runSync(block: suspend () -> T): T {
    val result = block.startCoroutineUninterceptedOrReturn(NoopContinuation)
    if (result == COROUTINE_SUSPENDED) {
        throw IllegalStateException("function passed to runSync suspended")
    }
    @Suppress("UNCHECKED_CAST")
    return result as T
}

fun CoroutineContext.executor(): Executor = object : Executor, CoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = this@executor

    override fun execute(command: Runnable?) {
        launch { command?.run() }
    }
}

object NoopContinuation : Continuation<Any?> {
    override fun resumeWith(result: Result<Any?>) {}

    override val context: CoroutineContext = EmptyCoroutineContext
}
