package org.jetbrains.ktor.pipeline

import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.future.*
import org.jetbrains.ktor.application.*
import java.util.concurrent.*

suspend fun <C : Any> PipelineContext<C>.runAsync(exec: Executor, block: PipelineInterceptor<C>) {
    // do async
    block(this, subject)
}

fun ApplicationCall.executeOn(exec: Executor, pipeline: Pipeline<ApplicationCall>): CompletableFuture<PipelineState> {
    return future(exec.toCoroutineDispatcher()) {
        pipeline.execute(this@executeOn)
        PipelineState.Finished
    }
}
