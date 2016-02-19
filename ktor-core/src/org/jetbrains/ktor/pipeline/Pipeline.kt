package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class Pipeline<T: ApplicationCall> {
    val blockBuilders = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        blockBuilders.add(block)
    }

    fun execute(value: T) : PipelineExecution.State {
        val execution = PipelineExecution(value, blockBuilders)
        execution.proceed()
        return execution.state
    }
}

