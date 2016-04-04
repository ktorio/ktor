package org.jetbrains.ktor.pipeline

class Pipeline<T : Any>() {
    val interceptors = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    constructor(interceptors: List<PipelineContext<T>.(T) -> Unit>) : this() {
        this.interceptors.addAll(interceptors)
    }

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        interceptors.add(block)
    }

    fun execute(subject: T): PipelineExecution<T> {
        val execution = PipelineExecution(subject, interceptors)
        execution.proceed()
        return execution
    }
}

