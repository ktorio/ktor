package org.jetbrains.ktor.pipeline

import org.jetbrains.ktor.application.*

class PipelineExecution<T : ApplicationCall>(val call: T, val blockBuilders: List<PipelineContext<T>.(T) -> Unit>) {
    enum class State {
        Execute, Pause, Finished;

        fun finished(): Boolean = this == Finished
    }

    var state = State.Pause
    val stack = mutableListOf<PipelineContext<T>>()

    fun proceed() {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val block = PipelineContext<T>(this, builder)
            stack.add(block)
            try {
                block.function(block, call)
            } catch(exception: Throwable) {
                fail(exception)
                return
            }

            when (block.signal) {
                State.Pause -> {
                    state = State.Pause
                    return
                }
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

    fun fail(exception: Throwable) {
        for (block in stack.asReversed()) {
            block.failures.forEach { it(exception) }
        }
        state = State.Finished
    }
}