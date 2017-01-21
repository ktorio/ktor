package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*
import java.util.concurrent.*
import java.util.function.*

suspend fun <C : Any> PipelineContext<C>.runAsync(exec: Executor, block: PipelineInterceptor<C>) {
    // do async
    block(this, subject)
}

fun ApplicationCall.executeOn(exec: Executor, pipeline: Pipeline<ApplicationCall>): CompletableFuture<PipelineState> {
    return CompletableFuture.supplyAsync(Supplier {
        pipeline.execute(this@executeOn)
        PipelineState.Finished
    }, exec)
}
