package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class PipelineContext<T : ApplicationCall>(private val execution: PipelineExecution<T>, val function: PipelineContext<T>.(T) -> Unit) {
    val exits = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()

    val call: T get() = execution.call
    val pipeline: PipelineControl<T> get() = execution

    fun onFinish(body: () -> Unit) {
        exits.add(body)
    }

    fun onFail(body: (Throwable) -> Unit) {
        failures.add(body)
    }
}