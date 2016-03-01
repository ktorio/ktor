package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

interface PipelineControl<T : ApplicationCall> {
    fun stop()
    fun pause()
    fun proceed()
    fun fail(exception: Throwable)
}

class PipelineExecution<T : ApplicationCall>(val call: T, val blockBuilders: MutableList<PipelineContext<T>.(T) -> Unit>) : PipelineControl<T> {
    enum class State {
        Execute, Pause, Finished;

        fun finished(): Boolean = this == Finished
    }

    var state = State.Pause
    val stack = mutableListOf<PipelineContext<T>>()

    override fun proceed() {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val block = PipelineContext(this, builder)
            stack.add(block)
            try {
                block.function(block, call)
            } catch(exception: Throwable) {
                fail(exception)
                return
            }

            when (state) {
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