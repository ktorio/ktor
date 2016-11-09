package org.jetbrains.ktor.pipeline

internal class PipelineExecution(
        val machine: PipelineMachine,
        override val subject: Any,
        val functions: List<PipelineContext<Any>.(Any) -> Unit>) : PipelineContext<Any> {

    val size = functions.size

    val blockSuccesses = arrayOfNulls<MutableList<PipelineContext<Any>.(Any) -> Unit>?>(size)
    val blockFailures = arrayOfNulls<MutableList<PipelineContext<Any>.(Any) -> Unit>?>(size)

    var blockIndex = 0
    var repeatIndex = 0

    var state = PipelineState.Executing
    override var exception: Throwable? = null


    override fun onSuccess(body: PipelineContext<Any>.(Any) -> Unit) {
        blockSuccesses.ensure(blockIndex - 1).add(body)
    }

    override fun onFail(body: PipelineContext<Any>.(Any) -> Unit) {
        blockFailures.ensure(blockIndex - 1).add(body)
    }

    override fun <T : Any> fork(value: T, pipeline: Pipeline<T>) = machine.execute(value, pipeline)
    override fun pause(): Nothing = machine.pause()

    override fun proceed() = machine.proceed()
    override fun fail(exception: Throwable): Nothing = machine.fail(exception)
    override fun finish(): Nothing = machine.finish()
    override fun finishAll(): Nothing = machine.finishAll()

    override fun repeat() {
        check(state == PipelineState.Executing) {
            "Repeating block is available only in Executing state"
        }
        check(repeatIndex == blockIndex - 1) {
            "Repeating block is available only from within the same block, and only once"
        }

        blockFailures[repeatIndex]?.clear()
        blockSuccesses[repeatIndex]?.clear()
        blockIndex = repeatIndex
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun Array<MutableList<PipelineContext<Any>.(Any) -> Unit>?>.ensure(index: Int): MutableList<PipelineContext<Any>.(Any) -> Unit> {
        val existing = this[index]
        if (existing != null) {
            return existing
        }

        val created = mutableListOf<PipelineContext<Any>.(Any) -> Unit>()
        this[index] = created
        return created
    }
}

