package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class PipelineContext<T : ApplicationCall>(val execution: PipelineExecution<T>, val function: PipelineContext<T>.(T) -> Unit) {
    var signal: PipelineExecution.State = PipelineExecution.State.Execute
    val exits = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()

    val call: T get() = execution.call

    fun onFinish(body: () -> Unit) {
        exits.add(body)
    }

    fun onFail(body: (Throwable) -> Unit) {
        failures.add(body)
    }

    fun finish() {
        signal = PipelineExecution.State.Finished
    }

    fun pause() {
        signal = PipelineExecution.State.Pause
    }
}