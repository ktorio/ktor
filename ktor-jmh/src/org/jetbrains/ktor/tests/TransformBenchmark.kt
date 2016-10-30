package org.jetbrains.ktor.tests

import org.jetbrains.ktor.transform.*
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

/*
TransformBenchmark.handlersSelectClass         thrpt   10  33104.770 ±  298.878  ops/ms
TransformBenchmark.handlersSelectExact         thrpt   10  50728.081 ± 2513.610  ops/ms
TransformBenchmark.handlersSelectInterface     thrpt   10  46478.099 ± 2555.443  ops/ms
TransformBenchmark.handlersTransform           thrpt   10  19637.388 ±  179.239  ops/ms
TransformBenchmark.subHandlersSelectClass      thrpt   10  14326.617 ±  714.602  ops/ms
TransformBenchmark.subHandlersSelectExact      thrpt   10  24108.043 ±  447.044  ops/ms
TransformBenchmark.subHandlersSelectInterface  thrpt   10  14650.240 ±  214.701  ops/ms
TransformBenchmark.subHandlersTransform        thrpt   10   7735.440 ±  283.133  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<TransformBenchmark>()
    }
}