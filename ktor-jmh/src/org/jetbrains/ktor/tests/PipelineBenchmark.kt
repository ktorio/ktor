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

    fun StateHolder.executeNext(depth: Int): Nothing {
        functions[index++]()
        if (index == functions.size)
            throw PipelineControl.Completed
        continueStateMachine(depth)
    }

    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    private fun continueStateMachine(depth: Int): Nothing {
        if (depth == 0) {
            throw PipelineControl.Continue
        }
        continueStateMachine(depth - 1)
    }

    @Benchmark
    fun stateMachine0(): PipelineState {
        return runStateMachine(0)
    }

    @Benchmark
    fun stateMachine1(): PipelineState {
        return runStateMachine(1)
    }

    @Benchmark
    fun stateMachine2(): PipelineState {
        return runStateMachine(2)
    }

    private fun runStateMachine(depth: Int): PipelineState {
        val state = StateHolder()
        loop@ while (true) {
            try {
                state.executeNext(depth)
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
Benchmark                        Mode  Cnt      Score      Error   Units
ActionPipeline.execute          thrpt   10  13204.180 ±  383.599  ops/ms
ActionPipeline2.execute         thrpt   10  10227.456 ±  528.144  ops/ms
ActionPipeline3.execute         thrpt   10   6771.575 ±  101.107  ops/ms

BaselinePipeline.directCalls    thrpt   10  54994.242 ± 1884.991  ops/ms
BaselinePipeline.stateMachine0  thrpt   10   3282.537 ±  172.657  ops/ms
BaselinePipeline.stateMachine1  thrpt   10   1805.310 ±   55.010  ops/ms
BaselinePipeline.stateMachine2  thrpt   10   1304.670 ±   52.871  ops/ms

ForkPipeline.execute            thrpt   10   3960.776 ±  106.883  ops/ms
ForkPipeline2.execute           thrpt   10   1586.361 ±   77.047  ops/ms
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