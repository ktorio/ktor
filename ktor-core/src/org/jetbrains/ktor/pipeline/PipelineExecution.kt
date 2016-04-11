package org.jetbrains.ktor.pipeline

internal class PipelineExecution<TSubject : Any>(
        val machine: PipelineMachine,
        override val subject: TSubject, functions: List<PipelineContext<TSubject>.(TSubject) -> Unit>) : PipelineContext<TSubject> {


    override fun onSuccess(body: () -> Unit) {
        blockStack.last().successes.add(body)
    }

    override fun onFail(body: (Throwable) -> Unit) {
        blockStack.last().failures.add(body)
    }

    override fun <T : Any> fork(value: T, pipeline: Pipeline<T>) = machine.execute(value, pipeline)
    override fun pause(): Nothing = machine.pause()
    override fun proceed() = machine.proceed()
    override fun fail(exception: Throwable): Nothing = machine.fail(exception)
    override fun finish(): Nothing = machine.finish()
    override fun finishAll(): Nothing = machine.finishAll()

    val blocks = functions.map { PipelineBlock(this, it) }
    val blockStack = mutableListOf<PipelineBlock<*>>()

    var state = PipelineState.Executing
    var exception: Throwable? = null
}

