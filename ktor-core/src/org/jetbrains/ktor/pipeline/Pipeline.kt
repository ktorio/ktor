package org.jetbrains.ktor.pipeline

class Pipeline<T> {
    val blockBuilders = mutableListOf<PipelineContext<T>.(T) -> Unit>()

    fun intercept(block: PipelineContext<T>.(T) -> Unit) {
        blockBuilders.add(block)
    }

    fun execute(value: T) {
        PipelineExecution(value, blockBuilders).proceed()
    }
}

class PipelineExecution<T>(val value: T, val blockBuilders: List<PipelineContext<T>.(T) -> Unit>) {
    val stack = mutableListOf<PipelineContext<T>>()

    fun proceed() {
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val block = PipelineContext<T>(this, builder)
            stack.add(block)
            block.execute(value)

            when (block.signal) {
                PipelineSignal.Pause -> return
                PipelineSignal.Stop -> break@loop
                PipelineSignal.Continue -> {}
            }
        }
        finish()
    }

    private fun finish() {
        for (block in stack.asReversed()) {
            block.exits.forEach { it() }
        }
    }

    private fun fail() {
        for (block in stack.asReversed()) {
            block.failures.forEach { it() }
        }
    }
}

enum class PipelineSignal {
    Continue, Pause, Stop
}

class PipelineContext<T>(private val execution: PipelineExecution<T>, private val function: PipelineContext<T>.(T) -> Unit) {
    var signal: PipelineSignal = PipelineSignal.Continue
    val exits = mutableListOf<() -> Unit>()
    val failures = mutableListOf<() -> Unit>()

    fun exit(body: () -> Unit) {
        exits.add(body)
    }

    fun failure(body: () -> Unit) {
        failures.add(body)
    }

    fun finish() {
        signal = PipelineSignal.Stop
    }

    fun pause() {
        signal = PipelineSignal.Pause
    }

    fun resume() {
        execution.proceed()
    }

    fun execute(value: T) {
        function(value)
    }
}