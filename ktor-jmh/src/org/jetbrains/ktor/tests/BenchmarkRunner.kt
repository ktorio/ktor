package org.jetbrains.ktor.tests

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.util.concurrent.*

val iterations = 100000
val defaultOptions = OptionsBuilder()
        .mode(Mode.Throughput)
        .timeUnit(TimeUnit.MILLISECONDS)
        .warmupIterations(7)
        .measurementIterations(25)
        .measurementTime(TimeValue.milliseconds(500))
        .forks(1)

class BenchmarkBuilder {
    val classes = mutableListOf<Class<*>>()
    fun add(clazz: Class<*>) {
        classes.add(clazz)
    }
}

fun benchmark(args: Array<String>, configure: BenchmarkBuilder.() -> Unit) {
    val options = BenchmarkBuilder().apply(configure)
    when (args.firstOrNull()) {
        "profile" -> runProfiler(options.classes)
        null, "benchmark" -> runJMH(options.classes)
    }
}

fun runProfiler(classes: List<Class<*>>) {
    classes.forEach {
        val instance = it.getConstructor().newInstance()
        val setups = it.methods.filter { it.annotations.any { it.annotationClass == Setup::class } }
        val benchmarks = it.methods.filter { it.annotations.any { it.annotationClass == Benchmark::class } }
        setups.forEach { it.invoke(instance) }

        repeat(iterations) {
            benchmarks.forEach { it.invoke(instance) }
        }
    }
}

fun runJMH(classes: List<Class<*>>) {
    val options = defaultOptions.apply {
        classes.forEach { include(it.name) }
    }
    Runner(options.build()).run()
}

inline fun <reified T : Any> BenchmarkBuilder.run() {
    add(T::class.java)
}