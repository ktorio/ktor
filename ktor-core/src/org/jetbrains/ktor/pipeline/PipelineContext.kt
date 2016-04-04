package org.jetbrains.ktor.pipeline

interface PipelineContext<TSubject : Any> {
    val subject: TSubject
    
    fun onSuccess(body: () -> Unit)
    fun onFail(body: (Throwable) -> Unit)

    fun pause()
    fun proceed()
    fun fail(exception: Throwable)
    fun finish()
}


internal class PipelineContextImpl<TSubject : Any>(private val execution: PipelineExecution<TSubject>, val function: PipelineContext<TSubject>.(TSubject) -> Unit) : PipelineContext<TSubject> {
    val successes = mutableListOf<() -> Unit>()
    val failures = mutableListOf<(Throwable) -> Unit>()
    var state = PipelineExecution.State.Pause

    override fun proceed(): Unit = execution.proceed()
    override fun fail(exception: Throwable) = execution.fail(exception)
    override fun pause() {
        state = PipelineExecution.State.Pause
    }

    override fun finish() {
        state = PipelineExecution.State.Succeeded
    }

    override val subject: TSubject get() = execution.subject

    override fun onSuccess(body: () -> Unit) {
        successes.add(body)
    }

    override fun onFail(body: (Throwable) -> Unit) {
        failures.add(body)
    }
}
