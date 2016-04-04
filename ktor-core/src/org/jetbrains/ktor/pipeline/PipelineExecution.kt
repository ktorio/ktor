package org.jetbrains.ktor.pipeline

class PipelineExecution<TSubject : Any>(val subject: TSubject, val blockBuilders: List<PipelineContext<TSubject>.(TSubject) -> Unit>) {
    enum class State {
        Execute, Pause, Succeeded, Failed;

        fun finished(): Boolean = this == Succeeded || this == Failed
    }

    var state = State.Pause
    private val stack = mutableListOf<PipelineContextImpl<TSubject>>()

    fun <TSecondary : Any> fork(subject: TSecondary,
                                pipeline: Pipeline<TSecondary>,
                                start: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit,
                                finish: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit
    ): Nothing {

        stack.last().pause()

        val primary = this@PipelineExecution
        var secondary: PipelineExecution<TSecondary>? = null

        val linkBack: PipelineContext<TSecondary>.(TSecondary) -> Unit = { subject ->
            onSuccess { finish(primary, secondary!!) }
            onFail { primary.fail(it) }
            start(primary, secondary!!)
        }

        secondary = PipelineExecution(subject, listOf(linkBack) + pipeline.interceptors)
        secondary.proceed()
    }

    fun proceed(): Nothing {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val context = PipelineContextImpl(this, builder)
            stack.add(context)
            try {
                context.state = State.Execute
                context.function(context, subject)
            } catch(f: PipelineBranchCompleted) {
                throw f
            } catch(assertion: AssertionError) {
                throw assertion // do not prevent tests from failing
            } catch(exception: Throwable) {
                fail(exception)
                throw PipelineBranchCompleted()
            }

            when (context.state) {
                State.Pause -> {
                    if (state == State.Execute)
                        state = State.Pause
                }
                State.Succeeded -> break@loop
                State.Failed -> break@loop
                State.Execute -> continue@loop
            }
        }
        if (state == State.Execute)
            finish()

        throw PipelineBranchCompleted()
    }

    fun finish() {
        try {
            while (stack.size > 0) {
                val item = stack.removeAt(stack.lastIndex)
                val handlers = item.successes
                while (handlers.size > 0) {
                    val handler = handlers.removeAt(handlers.lastIndex)
                    handler()
                }
            }
        } catch(f: PipelineBranchCompleted) {
            throw f
        } catch (t: Throwable) {
            fail(t)
            return
        }
    }

    fun fail(exception: Throwable) {
        while (stack.size > 0) {
            val item = stack.removeAt(stack.lastIndex)
            val handlers = item.failures
            while (handlers.size > 0) {
                val handler = handlers.removeAt(handlers.lastIndex)
                try {
                    handler(exception)
                } catch(t: Throwable) {
                    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
                    (exception as java.lang.Throwable).addSuppressed(t)
                }
            }
        }
        state = State.Failed
    }
}