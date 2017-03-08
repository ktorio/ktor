package org.jetbrains.ktor.tests

import org.jetbrains.ktor.transform.*
import org.jetbrains.ktor.util.*
import org.openjdk.jmh.annotations.*

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
    fun handlersTransform() = runSync { table.transform(Unit, O) }

    @Benchmark
    fun subHandlersSelectExact() = subTable.handlers(I::class.java)

    @Benchmark
    fun subHandlersSelectInterface() = subTable.handlers(M::class.java)

    @Benchmark
    fun subHandlersSelectClass() = subTable.handlers(C::class.java)

    @Benchmark
    fun subHandlersTransform() = runSync { subTable.transform(Unit, O) }

    interface I
    interface M : I
    open class C : M

    object O : C()
}

/*
TransformBenchmark.handlersSelectClass         thrpt   20  47604.798 ± 1676.656  ops/ms
TransformBenchmark.handlersSelectExact         thrpt   20  47700.687 ± 1577.830  ops/ms
TransformBenchmark.handlersSelectInterface     thrpt   20  44411.919 ± 2571.797  ops/ms
TransformBenchmark.handlersTransform           thrpt   20  12168.531 ±  505.025  ops/ms
TransformBenchmark.subHandlersSelectClass      thrpt   20  44466.004 ± 1993.300  ops/ms
TransformBenchmark.subHandlersSelectExact      thrpt   20  46456.387 ± 1514.119  ops/ms
TransformBenchmark.subHandlersSelectInterface  thrpt   20  42702.777 ± 2455.606  ops/ms
TransformBenchmark.subHandlersTransform        thrpt   20  12627.306 ±  108.101  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<TransformBenchmark>()
    }
}