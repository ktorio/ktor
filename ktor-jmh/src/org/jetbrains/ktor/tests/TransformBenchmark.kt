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
TransformBenchmark.handlersSelectClass         thrpt   10  49115.226 ± 3019.768  ops/ms
TransformBenchmark.handlersSelectExact         thrpt   10  44736.116 ± 4150.297  ops/ms
TransformBenchmark.handlersSelectInterface     thrpt   10  45388.982 ± 2390.448  ops/ms
TransformBenchmark.handlersTransform           thrpt   10  13724.432 ±  849.569  ops/ms
TransformBenchmark.subHandlersSelectClass      thrpt   10  13706.470 ± 1096.372  ops/ms
TransformBenchmark.subHandlersSelectExact      thrpt   10  24532.588 ±  651.986  ops/ms
TransformBenchmark.subHandlersSelectInterface  thrpt   10  13546.368 ± 1068.164  ops/ms
TransformBenchmark.subHandlersTransform        thrpt   10   6891.998 ±  200.673  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<TransformBenchmark>()
    }
}