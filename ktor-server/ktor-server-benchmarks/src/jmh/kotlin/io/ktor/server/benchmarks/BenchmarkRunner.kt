package io.ktor.server.benchmarks

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.results.format.*
import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.lang.reflect.*
import java.util.concurrent.*
import kotlin.concurrent.*

val numberOfOperations = 10000
val jmhOptions = OptionsBuilder()
        .mode(Mode.Throughput)
        .timeUnit(TimeUnit.MILLISECONDS)
        .resultFormat(ResultFormatType.CSV)
        .forks(1)

class BenchmarkSettings {
    var threads = 32
    val profilers = mutableListOf<String>()
    var iterations = 20
    var iterationTime = 1000L
    val benchmarks = mutableListOf<BenchmarkDescriptor>()

    fun profile(name: String) {
        profilers.add(name)
    }

    inline fun <reified T : Any> run(method: String? = null) {
        add(T::class.java, method)
    }

    fun add(clazz: Class<*>, method: String? = null) {
        benchmarks.add(BenchmarkDescriptor(clazz, method))
    }
}

data class BenchmarkDescriptor(val clazz: Class<*>, val method: String? = null)

fun benchmark(args: Array<String>, configure: BenchmarkSettings.() -> Unit) {
    val options = BenchmarkSettings().apply(configure)
    when (args.firstOrNull()) {
        "daemon" -> runDaemon(options)
        "profile" -> runProfiler(options)
        null, "benchmark" -> runJMH(options)
    }
}

fun runDaemon(settings: BenchmarkSettings) {
    val (clazz, method) = settings.benchmarks.singleOrNull() ?: throw IllegalArgumentException("Daemon mode supports only single benchmark")
    println("${clazz.name}.${method ?: "*"}")
    val instance = clazz.getConstructor().newInstance()
    val setups = clazz.methods.filter { it.annotations.any { it.annotationClass == Setup::class } }
    val teardowns = clazz.methods.filter { it.annotations.any { it.annotationClass == TearDown::class } }
    if (setups.isNotEmpty()) {
        println("Setting up…")
        setups.forEach { it.invoke(instance) }
    }
    println("Press ENTER to exit")
    readLine()
    if (teardowns.isNotEmpty()) {
        println("Tearing down…")
        teardowns.forEach { it.invoke(instance) }
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
            println("Running $numberOfOperations iterations…")
            instance.executeBenchmarks(benchmarks, numberOfOperations)
        } else {
            val iterationsPerThread = numberOfOperations / settings.threads
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
            try {
                benchmark.invoke(this)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}

fun runJMH(settings: BenchmarkSettings) {
    val options = jmhOptions.apply {
        settings.profilers.forEach {
            addProfiler(it)
        }
        measurementIterations(settings.iterations)
        warmupIterations(settings.iterations)
        warmupTime(TimeValue.milliseconds(settings.iterationTime))
        measurementTime(TimeValue.milliseconds(settings.iterationTime))

        threads(settings.threads)
        settings.benchmarks.forEach { (clazz, method) ->
            val regexp = clazz.name + (method?.let { ".$it" } ?: "")
            include(regexp.replace(".", "\\."))
        }

        System.getProperty("benchmarkClassFqName")?.let { fqName ->
            val name = fqName.substringAfterLast('.').removeSuffix("Kt")
            result("build/reports/benchmarks/$name.csv")
        }
    }
    Runner(options.build()).run()
}
