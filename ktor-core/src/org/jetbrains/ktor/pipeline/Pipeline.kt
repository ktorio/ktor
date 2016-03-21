package org.jetbrains.ktor.pipeline

class Pipeline<T> {
    val interceptors = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        interceptors.add(block)
    }

    fun execute(call: T) : PipelineExecution<T> {
        val execution = PipelineExecution(call, interceptors)
        execution.proceed()
        return execution
    }
}

