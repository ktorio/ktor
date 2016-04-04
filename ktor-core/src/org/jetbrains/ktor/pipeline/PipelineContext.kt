package org.jetbrains.ktor.pipeline

class PipelineContext<TSubject : Any>(private val execution: PipelineExecution<TSubject>, val function: PipelineContext<TSubject>.(TSubject) -> Unit) : PipelineControl {

    override fun fail(exception: Throwable) {
        execution.fail(exception)
    }

    override fun pause() {
        state = PipelineExecution.State.Pause
    }

    override fun proceed() {
        execution.proceed()
    }

    override fun finish() {
        state = PipelineExecution.State.Succeeded
    }

    val successes = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()

    val subject: TSubject get() = execution.subject
    val pipeline: PipelineControl get() = this

    var state = PipelineExecution.State.Pause

    fun onSuccess(body: () -> Unit) {
        successes.add(body)
    }

    fun onFail(body: (Throwable) -> Unit) {
        failures.add(body)
    }
}
