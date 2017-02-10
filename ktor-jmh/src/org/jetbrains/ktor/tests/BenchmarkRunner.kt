package org.jetbrains.ktor.tests

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.lang.reflect.*
import java.util.concurrent.*
import kotlin.concurrent.*

val iterations = 10000
val jmhOptions = OptionsBuilder()
        .mode(Mode.Throughput)
        .timeUnit(TimeUnit.MILLISECONDS)
        .warmupIterations(10)
        .measurementIterations(10)
        .warmupTime(TimeValue.milliseconds(500))
        .measurementTime(TimeValue.milliseconds(500))
        .forks(1)

class BenchmarkSettings {
    var threads = 1
    val benchmarks = mutableListOf<BenchmarkDescriptor>()
    fun add(clazz: Class<*>, method: String? = null) {
        benchmarks.add(BenchmarkDescriptor(clazz, method))
    }
}

data class BenchmarkDescriptor(val clazz: Class<*>, val method: String? = null)

fun benchmark(args: Array<String>, configure: BenchmarkSettings.() -> Unit) {
    val options = BenchmarkSettings().apply(configure)
    when (args.firstOrNull()) {
        "profile" -> runProfiler(options)
        null, "benchmark" -> runJMH(options)
    }
}

fun runProfiler(settings: BenchmarkSettings) {
    settings.benchmarks.forEach { (clazz, method) ->
        println("${clazz.name}.${method ?: "*"}")
        val instance = clazz.getConstructor().newInstance()
        val setups = clazz.methods.filter { it.annotations.any { it.annotationClass == Setup::class } }
        val teardowns = clazz.methods.filter { it.annotations.any { it.annotationClass == TearDown::class } }
        val allBenchmarks = clazz.methods.filter { it.annotations.any { it.annotationClass == Benchmark::class } }
        val benchmarks = if (method == null) allBenchmarks else allBenchmarks.filter { it.name == method }

        if (setups.isNotEmpty()) {
            println("Setting up…")
            setups.forEach { it.invoke(instance) }
        }

        println("Warming up…")
        benchmarks.forEach { it.invoke(instance) }

        if (settings.threads == 1) {
            println("Running $iterations iterations…")
            instance.executeBenchmarks(benchmarks, iterations)
        } else {
            val iterationsPerThread = iterations / settings.threads
            println("Running ${settings.threads} threads with $iterationsPerThread iterations per thread…")
            val threads = (1..settings.threads).map { index ->
                thread(name = "Test Thread $index") {
                    println("Started thread '${Thread.currentThread().name}'")
                    instance.executeBenchmarks(benchmarks, iterationsPerThread)
                    println("Finished thread '${Thread.currentThread().name}'")
                }
            }
            threads.forEach {
                it.join()
            }
        }

        if (teardowns.isNotEmpty()) {
            println("Tearing down…")
            teardowns.forEach { it.invoke(instance) }
        }
    }
}

private fun Any?.executeBenchmarks(benchmarks: List<Method>, iterations: Int) {
    benchmarks.forEach { benchmark ->
        repeat(iterations) {
            benchmark.invoke(this)
        }
    }
}

fun runJMH(settings: BenchmarkSettings) {
    val options = jmhOptions.apply {
        threads(settings.threads)
        settings.benchmarks.forEach { (clazz, method) ->
            val regexp = clazz.name + (method?.let { ".$it" } ?: "")
            include(regexp.replace(".", "\\."))
        }
    }
    Runner(options.build()).run()
}

inline fun <reified T : Any> BenchmarkSettings.run(method: String? = null) {
    add(T::class.java, method)
}

fun main(args: Array<String>) {
    benchmark(args) {
        run<CodecsBenchmark>()
        run<FullBenchmark>()
        run<IntegrationBenchmark>()
        run<PipelineBenchmark>()
        run<RoutingBenchmark>()
        run<TransformBenchmark>()
        run<ValuesMapBenchmark>()
    }
}