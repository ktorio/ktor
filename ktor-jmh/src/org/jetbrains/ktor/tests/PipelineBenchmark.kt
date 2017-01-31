package org.jetbrains.ktor.tests

import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class BaselinePipeline {
    val functions = listOf({ 1 }, { 2 }, { 3 })
    val suspendFunctions = listOf<suspend () -> Int>({ 1 }, { 2 }, { 3 })

    @Benchmark
    fun directCalls(): Int {
        return functions.sumBy { it() }
    }

    @Benchmark
    fun suspendCalls(): Int {
        return runSync {
            suspendFunctions.sumBy { it() }
        }
    }
}

@State(Scope.Benchmark)
abstract class PipelineBenchmark {
    val callPhase = PipelinePhase("Call")
    fun pipeline(): Pipeline<String> = Pipeline(callPhase)
    fun Pipeline<String>.intercept(block: suspend PipelineContext<String>.(String) -> Unit) = phases.intercept(callPhase, block)

    fun <T : Any> Pipeline<T>.executeBlocking(subject: T): PipelineState {
        try {
            runSync { execute(subject) }
        } catch (t: Throwable) {
            return PipelineState.Failed
        }
        return PipelineState.Finished
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
        return pipeline.executeBlocking("some")
    }
}

open class PipelineFork : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        val another = pipeline()
        another.intercept { proceed() }
        pipeline.intercept {
            another.execute("another")
            proceed()
        }
    }
}

open class PipelineFork2 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        val another = pipeline()
        val double = pipeline()
        double.intercept { proceed() }
        another.intercept {
            double.execute("double")
            proceed()
        }
        pipeline.intercept {
            another.execute("another")
            proceed()
        }
    }
}

open class PipelineFork2Implicit : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        val another = pipeline()
        val double = pipeline()
        double.intercept { }
        another.intercept { double.execute("double") }
        pipeline.intercept { another.execute("another") }
    }
}

open class PipelineAction : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction2 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction3 : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction3Implicit : PipelineBenchmark() {
    override fun Pipeline<String>.configure() {
        pipeline.intercept { }
        pipeline.intercept { }
        pipeline.intercept { }
    }
}

/*
BaselinePipeline.directCalls     thrpt   10  46760.335 ± 1067.980  ops/ms
BaselinePipeline.suspendCalls    thrpt   10  22505.835 ± 1989.106  ops/ms

PipelineAction.execute           thrpt   10  20156.554 ± 2235.894  ops/ms
PipelineAction2.execute          thrpt   10  15280.387 ±  247.277  ops/ms
PipelineAction3.execute          thrpt   10  11361.312 ± 1358.427  ops/ms
PipelineAction3Implicit.execute  thrpt   10  15664.903 ± 1489.025  ops/ms
PipelineFork.execute             thrpt   10  10688.013 ±  732.490  ops/ms
PipelineFork2.execute            thrpt   10   8955.689 ±  227.900  ops/ms
PipelineFork2Implicit.execute    thrpt   10  11414.864 ±  378.706  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<BaselinePipeline>()
        run<PipelineAction>()
        run<PipelineAction2>()
        run<PipelineAction3>()
        run<PipelineAction3Implicit>()
        run<PipelineFork>()
        run<PipelineFork2>()
        run<PipelineFork2Implicit>()
    }
}