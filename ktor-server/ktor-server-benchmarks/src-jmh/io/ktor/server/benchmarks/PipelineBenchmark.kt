package io.ktor.server.benchmarks

import io.ktor.application.*
import io.ktor.util.cio.*
import io.ktor.util.pipeline.*
import io.ktor.server.testing.*
import org.openjdk.jmh.annotations.*
import kotlin.coroutines.*

@State(Scope.Benchmark)
class BaselinePipeline {
    val functions = listOf({ "1" }, { "2" }, { "3" })
    val suspendFunctions = listOf<suspend () -> String>({ "1" }, { "2" }, { "3" })

    @Benchmark
    fun directCalls(): String {
        return functions.fold("") { a, b -> a + b() }
    }

    @Benchmark
    fun suspendCalls(): String {
        return runSync {
            suspendFunctions.fold("") { a, b -> a + b() }
        }
    }
}

@State(Scope.Benchmark)
abstract class PipelineBenchmark {
    val environment = createTestEnvironment()
    val host = TestApplicationEngine(environment).apply { start() }
    val call = TestApplicationCall(host.application, coroutineContext = EmptyCoroutineContext)

    val callPhase = PipelinePhase("Call")
    fun pipeline(): Pipeline<String, ApplicationCall> = Pipeline(callPhase)
    fun Pipeline<String, ApplicationCall>.intercept(block: PipelineInterceptor<String, ApplicationCall>) = intercept(callPhase, block)

    fun <T : Any> Pipeline<T, ApplicationCall>.executeBlocking(subject: T) = runSync { execute(call, subject) }

    lateinit var pipeline: Pipeline<String, ApplicationCall>

    @Setup(Level.Iteration)
    fun createPipeline() {
        pipeline = pipeline()
        pipeline.configure()
    }

    abstract fun Pipeline<String, ApplicationCall>.configure()

    @Benchmark
    fun execute() {
        pipeline.executeBlocking("some")
    }
}

open class PipelineFork : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        val another = pipeline()
        another.intercept { proceed() }
        pipeline.intercept {
            another.execute(call, "another")
            proceed()
        }
    }
}

open class PipelineFork2 : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        val another = pipeline()
        val double = pipeline()
        double.intercept { proceed() }
        another.intercept {
            double.execute(call, "double")
            proceed()
        }
        pipeline.intercept {
            another.execute(call, "another")
            proceed()
        }
    }
}

open class PipelineFork2Implicit : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        val another = pipeline()
        val double = pipeline()
        double.intercept { }
        another.intercept { double.execute(call, "double") }
        pipeline.intercept { another.execute(call, "another") }
    }
}

open class PipelineAction : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction2 : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction3 : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
        pipeline.intercept { proceed() }
    }
}

open class PipelineAction3Implicit : PipelineBenchmark() {
    override fun Pipeline<String, ApplicationCall>.configure() {
        pipeline.intercept { }
        pipeline.intercept { }
        pipeline.intercept { }
    }
}

/*
BaselinePipeline.directCalls     thrpt   10  10661.608 ± 2258.592  ops/ms
BaselinePipeline.suspendCalls    thrpt   10   6814.299 ± 1460.205  ops/ms

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