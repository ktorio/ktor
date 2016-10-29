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

fun benchmark(configure: ChainedOptionsBuilder.() -> Unit) {
    val options = defaultOptions.apply(configure)
    Runner(options.build()).run()
}

inline fun <reified T : Any> ChainedOptionsBuilder.include() {
    include(T::class.java.name)
}