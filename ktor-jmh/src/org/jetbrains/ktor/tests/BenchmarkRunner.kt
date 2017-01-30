package org.jetbrains.ktor.tests

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.util.concurrent.*
import kotlin.concurrent.*

val iterations = 1000
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
    val classes = mutableListOf<Class<*>>()
    fun add(clazz: Class<*>) {
        classes.add(clazz)
    }
}

fun benchmark(args: Array<String>, configure: BenchmarkSettings.() -> Unit) {
    val options = BenchmarkSettings().apply(configure)
    when (args.firstOrNull()) {
        "profile" -> runProfiler(options)
        null, "benchmark" -> runJMH(options)
    }
}

fun runProfiler(settings: BenchmarkSettings) {
    settings.classes.forEach {
        val instance = it.getConstructor().newInstance()
        val setups = it.methods.filter { it.annotations.any { it.annotationClass == Setup::class } }
        val teardowns = it.methods.filter { it.annotations.any { it.annotationClass == TearDown::class } }
        val benchmarks = it.methods.filter { it.annotations.any { it.annotationClass == Benchmark::class } }

        if (setups.isNotEmpty()) {
            println("Setting up…")
            setups.forEach { it.invoke(instance) }
        }

        if (settings.threads == 1) {
            println("Running $iterations iterations…")
            repeat(iterations) {
                benchmarks.forEach { it.invoke(instance) }
            }
        } else {
            val iterationsPerThread = iterations / settings.threads
            println("Running ${settings.threads} threads with $iterationsPerThread iterations per thread…")
            val threads = (1..settings.threads).map { index ->
                thread(name = "Test Thread $index") {
                    println("Started thread '${Thread.currentThread().name}'")
                    repeat(iterationsPerThread) {
                        benchmarks.forEach { it.invoke(instance) }
                    }
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

fun runJMH(settings: BenchmarkSettings) {
    val options = jmhOptions.apply {
        threads(settings.threads)
        settings.classes.forEach { include(it.name) }
    }
    Runner(options.build()).run()
}

inline fun <reified T : Any> BenchmarkSettings.run() {
    add(T::class.java)
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