package org.jetbrains.ktor.pipeline

class Pipeline<T> {
    val blockBuilders = mutableListOf<PipelineBlock<T>.(T) -> Unit>()

    fun intercept(block: PipelineBlock<T>.(T) -> Unit) {
        blockBuilders.add(block)
    }

    fun execute(call: T) {
        PipelineState(call, blockBuilders).proceed()
    }
}

class PipelineState<T>(val call: T, val blockBuilders: List<PipelineBlock<T>.(T) -> Unit>) {
    val stack = mutableListOf<PipelineBlock<T>>()

    fun proceed() {
        while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val block = PipelineBlock(builder)
            stack.add(block)
            block.execute(call)
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

class PipelineBlock<T>(val enter: PipelineBlock<T>.(T) -> Unit) {
    val exits = mutableListOf<() -> Unit>()
    val failures = mutableListOf<() -> Unit>()

    fun exit(body: () -> Unit) {
        exits.add(body)
    }

    fun failure(body: () -> Unit) {
        failures.add(body)
    }

    fun execute(call: T) {
        enter(call)
    }
}