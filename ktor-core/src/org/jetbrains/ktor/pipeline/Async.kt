package org.jetbrains.ktor.pipeline

import java.util.concurrent.*

fun <C : Any> PipelineContext<C>.runAsync(exec: ExecutorService, block: PipelineContext<C>.() -> Unit): Nothing {
    exec.submit {
        runBlock(block)
    }

    pause()
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
        continueMachine()
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
        continueMachine()
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
    // shouldn't kotlin report error here?
}

inline fun PipelineMachine.runBlockWithResult(block: () -> Unit): PipelineState {
    try {
        runBlock(block)
    } catch (e: PipelineCompleted) {
        return PipelineState.Succeeded
    } catch (e: PipelinePaused) {
        return PipelineState.Executing
    }
    // shouldn't kotlin report error here?
}

fun PipelineContext<*>.continueMachine(): Nothing {
    while (true) {
        try {
            proceed()
        } catch (e: PipelineContinue) {
            continue
        }
    }
}

fun PipelineMachine.continueMachine(): Nothing {
    while (true) {
        try {
            proceed()
        } catch (e: PipelineContinue) {
            continue
        }
    }
}