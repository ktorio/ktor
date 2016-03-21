package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class PipelineContext<T : ApplicationCall>(private val execution: PipelineExecution<T>, val function: PipelineContext<T>.(T) -> Unit) : PipelineControl<T> {
    override fun fork(call: T, pipeline: Pipeline<T>) {
        execution.fork(call, pipeline)
    }

    override fun fail(exception: Throwable) {
        execution.fail(exception)
    }

    override fun pause() {
        state = PipelineExecution.State.Pause
    }

    override fun proceed() {
        execution.proceed()
    }

    override fun stop() {
        state = PipelineExecution.State.Finished
    }

    val exits = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()

    val call: T get() = execution.call
    val pipeline: PipelineControl<T> get() = this

    var state = PipelineExecution.State.Pause

    fun onFinish(body: () -> Unit) {
        exits.add(body)
    }

    fun onFail(body: (Throwable) -> Unit) {
        failures.add(body)
    }

    fun execute(call: T) {
        state = PipelineExecution.State.Execute
        function(call)
    }
}