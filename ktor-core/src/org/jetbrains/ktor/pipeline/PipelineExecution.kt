package org.jetbrains.ktor.pipeline

import java.util.concurrent.*

interface PipelineControl<TSubject : Any> {
    fun finish()
    fun pause()
    fun proceed()
    fun fail(exception: Throwable)

    fun <TSecondary : Any> fork(subject: TSecondary,
                                pipeline: Pipeline<TSecondary>,
                                attach: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit,
                                detach: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit)
}

fun <T : Any> PipelineControl<T>.join(future: CompletionStage<*>) {
    pause()
    future.whenComplete { unit, throwable ->
        if (throwable == null)
            proceed()
        else
            fail(throwable)
    }
}


class PipelineExecution<TSubject : Any>(val subject: TSubject, val blockBuilders: List<PipelineContext<TSubject>.(TSubject) -> Unit>) {

    enum class State {
        Execute, Pause, Finished;

        fun finished(): Boolean = this == Finished
    }

    var state = State.Pause
    private val stack = mutableListOf<PipelineContext<TSubject>>()

    fun <TSecondary : Any> fork(subject: TSecondary,
                                pipeline: Pipeline<TSecondary>,
                                attach: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit,
                                detach: (PipelineExecution<TSubject>, PipelineExecution<TSecondary>) -> Unit
    ): PipelineExecution<TSecondary> {
        val primary = this@PipelineExecution
        var secondary: PipelineExecution<TSecondary>? = null
        val chain: PipelineContext<TSecondary>.(TSecondary) -> Unit = { subject ->
            onFinish {
                detach(primary, secondary!!)
            }
            onFail {
                // TODO: ? detach(primary, secondary!!)
                primary.fail(it)
            }
            attach(primary, secondary!!)
        }
        val interceptors = listOf(chain) + pipeline.interceptors
        secondary = PipelineExecution(subject, interceptors)

        val currentMaster = stack.last()
        currentMaster.state = State.Pause
        secondary.proceed()
        if (secondary.state.finished()) {
            // if secondary completed, master it already finished in `chain`
            currentMaster.state = State.Finished
        }
        return secondary
    }

    fun proceed() {
        state = State.Execute
        loop@while (stack.size < blockBuilders.size) {
            val index = stack.size
            val builder = blockBuilders[index]
            val context = PipelineContext(this, builder)
            stack.add(context)
            try {
                context.state = State.Execute
                context.function(context, subject)
            } catch(assertion: AssertionError) {
                throw assertion // do not prevent tests from failing
            } catch(exception: Throwable) {
                fail(exception)
                return
            }

            when (context.state) {
                State.Pause -> {
                    if (state == State.Execute)
                        state = State.Pause
                    return
                }
                State.Finished -> break@loop
                State.Execute -> continue@loop
            }
        }
        if (state == State.Execute)
            finish()
    }

    fun finish() {
        try {
            while (stack.size > 0) {
                val item = stack.removeAt(stack.lastIndex)
                val handlers = item.exits
                while (handlers.size > 0) {
                    val handler = handlers.removeAt(handlers.lastIndex)
                    handler()
                }
            }
        } catch (t: Throwable) {
            fail(t)
            return
        }
        state = State.Finished
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
        state = State.Finished
    }
}