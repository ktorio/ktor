package org.jetbrains.ktor.pipeline

import java.util.concurrent.*

interface PipelineControl<T> {
    fun stop()
    fun pause()
    fun proceed()
    fun fail(exception: Throwable)

    fun fork(subject: T, pipeline: Pipeline<T>)

}

fun <T> PipelineControl<T>.join(future: CompletionStage<*>) {
    pause()
    future.whenComplete { unit, throwable ->
        if (throwable == null)
            proceed()
        else
            fail(throwable)
    }
}


class PipelineExecution<T>(val subject: T, val blockBuilders: List<PipelineContext<T>.(T) -> Unit>) {

    enum class State {
        Execute, Pause, Finished;

        fun finished(): Boolean = this == Finished
    }

    var state = State.Pause
    private val stack = mutableListOf<PipelineContext<T>>()

    fun fork(subject: T, pipeline: Pipeline<T>) {
        val context = stack.last()
        context.pause()
        val resume: PipelineContext<T>.(T) -> Unit = { subject ->
            onFinish { context.proceed() }
            onFail { context.fail(it) }
        }
        val builders = listOf(resume) + pipeline.blockBuilders
        val execution = PipelineExecution(subject, builders)
        execution.proceed()
    }

    internal fun proceed() {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val context = PipelineContext(this, builder)
            stack.add(context)
            try {
                context.execute(subject)
            } catch(assertion: AssertionError) {
                throw assertion // do not prevent tests from failing
            } catch(exception: Throwable) {
                fail(exception)
                return
            }

            when (context.state) {
                State.Pause -> return
                State.Finished -> break@loop
                State.Execute -> continue@loop
            }
        }
        finish()
    }

    private fun finish() {
        for (block in stack.asReversed()) {
            block.exits.forEach { it() }
        }
        state = State.Finished
    }

    internal fun fail(exception: Throwable) {
        for (block in stack.asReversed()) {
            block.failures.forEach { it(exception) }
        }
        state = State.Finished
    }
}