package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*
import java.util.concurrent.*
import java.util.function.*

fun <C : Any> PipelineContext<C>.runAsync(exec: Executor, block: PipelineContext<C>.() -> Unit): Nothing {
    exec.execute {
        runBlockWithResult(block)
    }
    pause()
}

inline fun <C : Any> PipelineContext<C>.runBlock(block: PipelineContext<C>.() -> Unit): Nothing {
    try {
        block()
    } catch (e: PipelineControl.Continue) {
        // fall through to proceed
    } catch (e: PipelineControl) {
        throw e
    } catch (t: Throwable) {
        fail(t)
    }
    proceed()
}

fun ApplicationCall.executeOn(exec: Executor, pipeline: Pipeline<ApplicationCall>): CompletableFuture<PipelineState> {
    return CompletableFuture.supplyAsync(Supplier {
        execution.runBlockWithResult {
            execution.execute(this, pipeline)
        }
    }, exec)

}

inline fun PipelineMachine.runBlock(block: () -> Unit): Nothing {
    try {
        block()
    } catch (e: PipelineControl.Continue) {
        // fall through to proceed
    } catch (e: PipelineControl) {
        throw e
    } catch (t: Throwable) {
        fail(t)
    }
    proceed()
}

inline fun <C : Any> PipelineContext<C>.runBlockWithResult(block: PipelineContext<C>.() -> Unit): PipelineState {
    try {
        runBlock(block)
    } catch (e: PipelineControl.Completed) {
        return PipelineState.Finished
    } catch (e: PipelineControl.Paused) {
        return PipelineState.Executing
    }
}

inline fun PipelineMachine.runBlockWithResult(block: () -> Unit): PipelineState {
    try {
        runBlock(block)
    } catch (e: PipelineControl.Completed) {
        return PipelineState.Finished
    } catch (e: PipelineControl.Paused) {
        return PipelineState.Executing
    }
}