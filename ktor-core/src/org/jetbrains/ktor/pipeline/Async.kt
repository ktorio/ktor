package org.jetbrains.ktor.pipeline

import java.util.concurrent.*

fun <C : Any> PipelineContext<C>.runAsync(exec: ExecutorService, block: PipelineContext<C>.() -> Unit): Nothing {
    val future = CompletableFuture.runAsync(Runnable {
        block()
    }, exec)
    future.whenComplete { unit, throwable ->
        if (throwable == null)
            proceed()
        else if (throwable !is PipelineControlFlow)
            fail(throwable)
    }
    pause()
}