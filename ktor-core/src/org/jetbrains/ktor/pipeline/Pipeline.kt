package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class Pipeline<T: ApplicationCall> {
    val blockBuilders = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        blockBuilders.add(block)
    }

    fun execute(value: T) : PipelineExecution<T> {
        val execution = PipelineExecution(value, blockBuilders.toMutableList())
        execution.proceed()
        return execution
    }
}

