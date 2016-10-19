package org.jetbrains.ktor.tests.transform

import org.jetbrains.ktor.transform.*
import org.openjdk.jmh.annotations.*

import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.util.concurrent.*

@State(Scope.Benchmark)
open class TransformBenchmark {
    private val table = TransformTable<Unit>()
    private lateinit var subTable: TransformTable<Unit>

    @Setup
    fun configure() {
        table.register<I> { "I" }
        subTable = TransformTable(table)
        subTable.register<M> { "M" }
    }

    @Benchmark
    fun handlersSelectExact() = table.handlers(I::class.java)

    @Benchmark
    fun handlersSelectInterface() = table.handlers(M::class.java)

    @Benchmark
    fun handlersSelectClass() = table.handlers(C::class.java)

    @Benchmark
    fun handlersTransform() = table.transform(Unit, O)

    @Benchmark
    fun subHandlersSelectExact() = subTable.handlers(I::class.java)

    @Benchmark
    fun subHandlersSelectInterface() = subTable.handlers(M::class.java)

    @Benchmark
    fun subHandlersSelectClass() = subTable.handlers(C::class.java)

    @Benchmark
    fun subHandlersTransform() = subTable.transform(Unit, O)

    interface I
    interface M : I
    open class C : M

    object O : C()
}

fun main(args: Array<String>) {
    val options = OptionsBuilder()
            .mode(Mode.AverageTime)
            .timeUnit(TimeUnit.MICROSECONDS)
            .include(TransformBenchmark::class.java.name)
            .warmupIterations(7)
            .measurementIterations(25)
            .measurementTime(TimeValue.milliseconds(500))
            .jvm("/usr/java/jdk-9/bin/java")
            .forks(1)

    Runner(options.build()).run()

}