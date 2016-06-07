package org.jetbrains.ktor.pipeline

internal class PipelineBlock<TSubject : Any>(private val execution: PipelineExecution<TSubject>,
                                                       val function: PipelineContext<TSubject>.(TSubject) -> Unit) {

    val successes = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()

    fun call() = execution.function(execution.subject)
}
