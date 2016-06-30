package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

fun <C : Any> PipelineContext<C>.runAsync(exec: Executor, block: PipelineContext<C>.() -> Unit): Nothing {
    exec.execute {
        runBlock(block)
    }

    pause()
}

fun ApplicationCall.execute(): CompletableFuture<PipelineState> = executeOn(application.executor, application)

fun ApplicationCall.executeOn(exec: Executor, pipeline: Pipeline<ApplicationCall>): CompletableFuture<PipelineState> {
    val future = CompletableFuture<PipelineState>()

    exec.execute {
        try {
            future.complete(execute(pipeline))
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }

    return future
}

fun <S: Any> PipelineMachine.executeOn(exec: Executor, subject: S, pipeline: Pipeline<S>): CompletableFuture<PipelineState> {
    val future = CompletableFuture<PipelineState>()

    exec.execute {
        try {
            future.complete(runBlockWithResult {
                execute(subject, pipeline)
            })
        } catch (t: Throwable) {
            future.completeExceptionally(t)
        }
    }

    return future
}

inline fun <C : Any> PipelineContext<C>.runBlock(block: PipelineContext<C>.() -> Unit): Nothing {
    try {
        try {
            block()
        } catch (e: PipelineControlFlow) {
            throw e
        } catch (t: Throwable) {
            fail(t)
        }

        proceed()
    } catch (e: PipelineContinue) {
        continuePipeline()
    }
}

inline fun PipelineMachine.runBlock(block: () -> Unit): Nothing {
    try {
        try {
            block()
        } catch (e: PipelineControlFlow) {
            throw e
        } catch (t: Throwable) {
            fail(t)
        }

        proceed()
    } catch (e: PipelineContinue) {
        continuePipeline()
    }
}

inline fun <C : Any> PipelineContext<C>.runBlockWithResult(block: PipelineContext<C>.() -> Unit): PipelineState {
    try {
        runBlock(block)
    } catch (e: PipelineCompleted) {
        return PipelineState.Succeeded
    } catch (e: PipelinePaused) {
        return PipelineState.Executing
    }
}

inline fun PipelineMachine.runBlockWithResult(block: () -> Unit): PipelineState {
    try {
        runBlock(block)
    } catch (e: PipelineCompleted) {
        return PipelineState.Succeeded
    } catch (e: PipelinePaused) {
        return PipelineState.Executing
    }
}

fun PipelineContext<*>.continuePipeline(): Nothing {
    while (true) {
        try {
            proceed()
        } catch (e: PipelineContinue) {
            continue
        }
    }
}

fun PipelineMachine.continuePipeline(): Nothing {
    while (true) {
        try {
            proceed()
        } catch (e: PipelineContinue) {
            continue
        }
    }
}