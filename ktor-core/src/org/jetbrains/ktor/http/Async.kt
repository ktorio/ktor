package org.jetbrains.ktor.http

import org.jetbrains.ktor.pipeline.*
import java.util.concurrent.*

fun <C : Any> PipelineContext<C>.proceedAsync(exec: ExecutorService, block: PipelineContext<C>.() -> Unit) {
    val future = CompletableFuture.runAsync(Runnable { block() }, exec)
    pipeline.join(future)
}
