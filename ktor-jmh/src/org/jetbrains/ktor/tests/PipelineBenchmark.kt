package org.jetbrains.ktor.tests

import org.jetbrains.ktor.pipeline.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class BaselinePipeline {
    val functions = listOf({ PipelineState.Finished }, { PipelineState.Finished }, { PipelineState.Finished })

    @Benchmark
    fun directCalls() {
        functions.forEach { it() }
    }

    class StateHolder {
        var index = 0
    }

    fun StateHolder.executeNext(): Nothing {
        functions[index++]()
        if (index == functions.size)
            throw PipelineControl.Completed
        throw PipelineControl.Continue
    }

    @Benchmark
    fun stateMachine(): PipelineState {
        val state = StateHolder()
        loop@ while (true) {
            try {
                state.executeNext()
            } catch (e: PipelineControl) {
                when (e) {
                    is PipelineControl.Completed -> return PipelineState.Finished
                    is PipelineControl.Continue -> continue@loop
                    else -> throw e
                }
            }
        }
    }
}

@State(Scope.Benchmark)
abstract class PipelineBenchmark {
    val callPhase = PipelinePhase("Call")
    fun pipeline(): Pipeline<String> = Pipeline(callPhase)
    fun Pipeline<String>.intercept(block: PipelineContext<String>.(String) -> Unit) = phases.intercept(callPhase, block)
    fun <T : Any> Pipeline<T>.execute(subject: T): PipelineState {
        try {
            PipelineMachine().execute(subject, this)
        } catch (e: PipelineControl) {
            when (e) {
                is PipelineControl.Completed -> return PipelineState.Finished
                is PipelineControl.Paused -> return PipelineState.Executing
                else -> throw e
            }
        }
    }

    lateinit var pipeline: Pipeline<String>

    @Setup(Level.Iteration)
    fun createPipeline() {
        pipeline = pipeline()
        pipeline.configure()
    }

    abstract fun Pipeline<String>.configure()

    @Benchmark
    fun execute(): PipelineState {
        return pipeline.execute("some")
    }
}

open class ForkPipeline : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        val another = pipeline()
        another.intercept {}
        pipeline.intercept {
            fork("another", another)
        }
    }
}

open class ForkPipeline2 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        val another = pipeline()
        val double = pipeline()
        double.intercept {}
        another.intercept {
            fork("double", double)
        }
        pipeline.intercept {
            fork("another", another)
        }
    }
}

open class ActionPipeline : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept {}
    }
}
open class ActionPipeline2 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept {}
        pipeline.intercept {}
    }
}
open class ActionPipeline3 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept {}
        pipeline.intercept {}
        pipeline.intercept {}
    }
}

/*
ActionPipeline.execute         thrpt   10  12913.291 ± 1219.844  ops/ms
ActionPipeline2.execute        thrpt   10   9907.695 ±  258.292  ops/ms
ActionPipeline3.execute        thrpt   10   6470.315 ±  731.727  ops/ms
BaselinePipeline.directCalls   thrpt   10  59905.115 ± 5605.544  ops/ms
BaselinePipeline.stateMachine  thrpt   10  51473.441 ± 4388.521  ops/ms
ForkPipeline.execute           thrpt   10   3550.697 ±  388.819  ops/ms
ForkPipeline2.execute          thrpt   10   2022.782 ±  204.074  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<BaselinePipeline>()
        run<ActionPipeline>()
        run<ActionPipeline2>()
        run<ActionPipeline3>()
        run<ForkPipeline>()
        run<ForkPipeline2>()
    }
}