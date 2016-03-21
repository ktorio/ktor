package org.jetbrains.ktor.pipeline

class Pipeline<T> {
    val interceptors = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        interceptors.add(block)
    }

    fun execute(subject: T) : PipelineExecution<T> {
        val execution = PipelineExecution(subject, interceptors)
        execution.proceed()
        return execution
    }
}

