package org.jetbrains.ktor.pipeline

import java.util.concurrent.*

fun <C : Any> PipelineContext<C>.runAsync(exec: ExecutorService, block: PipelineContext<C>.() -> Unit): Nothing {
    exec.submit {
        asyncBlock(block)
    }

    pause()
}

private fun <C : Any> PipelineContext<C>.asyncBlock(block: PipelineContext<C>.() -> Unit): Nothing {
    try {
        try {
            block()
            proceed()
        } catch (e: PipelineControlFlow) {
            throw e
        } catch (t: Throwable) {
            fail(t)
        }
    } catch (e: PipelineContinue) {
        proceed()
    }
}