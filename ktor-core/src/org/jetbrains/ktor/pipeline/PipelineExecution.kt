package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*
import java.util.concurrent.*

interface PipelineControl<T : ApplicationCall> {
    fun stop()
    fun pause()
    fun proceed()
    fun fail(exception: Throwable)

    fun fork(call: T, pipeline: Pipeline<T>)

}

fun <T : ApplicationCall> PipelineControl<T>.join(future: CompletionStage<*>) {
    pause()
    future.whenComplete { unit, throwable ->
        if (throwable == null)
            proceed()
        else
            fail(throwable)
    }
}


class PipelineExecution<T : ApplicationCall>(val call: T, val blockBuilders: List<PipelineContext<T>.(T) -> Unit>) : PipelineControl<T> {

    enum class State {
        Execute, Pause, Finished;

        fun finished(): Boolean = this == Finished
    }

    var state = State.Pause
    val stack = mutableListOf<PipelineContext<T>>()

    override fun fork(call: T, pipeline: Pipeline<T>) {
        val context = stack.last()
        context.pause()
        val resume: PipelineContext<T>.(T) -> Unit = { call -> onFinish { context.proceed() } }
        val builders = listOf(resume) + pipeline.blockBuilders
        val execution = PipelineExecution(call, builders)
        execution.proceed()
    }

    override fun proceed() {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val context = PipelineContext(this, builder)
            stack.add(context)
            try {
                context.execute(call)
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

    override fun fail(exception: Throwable) {
        for (block in stack.asReversed()) {
            block.failures.forEach { it(exception) }
        }
        state = State.Finished
    }

    override fun stop() {
        state = PipelineExecution.State.Finished
    }

    override fun pause() {
        state = PipelineExecution.State.Pause
    }

}