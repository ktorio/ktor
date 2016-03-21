package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class Pipeline<T: ApplicationCall> {
    val blockBuilders = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        blockBuilders.add(block)
    }

    fun execute(call: T) : PipelineExecution<T> {
        val execution = PipelineExecution(call, blockBuilders)
        execution.proceed()
        return execution
    }
}

