package io.ktor.server.benchmarks

import io.ktor.http.*
import io.ktor.util.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
class ValuesMapBenchmark {
    private val headers = valuesOf("A" to listOf("B"), "C" to listOf("D"))

    @Benchmark
    fun valuesOfSingle(): ValuesMap {
        return valuesOf("A" to listOf("B"))
    }

    @Benchmark
    fun valuesOfMany(): ValuesMap {
        return valuesOf("A" to listOf("B"), "C" to listOf("D"))
    }

    @Benchmark
    fun build(): ValuesMap {
        return ValuesMap.build {
            append("A", "B")
            append("C", "D")
        }
    }

    @Benchmark
    fun filter(): ValuesMap {
        return headers.filter { _, _ -> true }
    }

    @Benchmark
    fun compression(): ValuesMap {
        return ValuesMap.build(true) {
            appendFiltered(headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, "deflate")
        }
    }
}

/*
Benchmark                           Mode  Cnt       Score      Error   Units
ValuesMapBenchmark.build           thrpt   10   10062.523 ±  636.484  ops/ms
ValuesMapBenchmark.compression     thrpt   10    4501.705 ±   73.952  ops/ms
ValuesMapBenchmark.filter          thrpt   10    9073.771 ±  662.824  ops/ms
ValuesMapBenchmark.valuesOfMany    thrpt   10   13795.421 ± 1234.157  ops/ms
ValuesMapBenchmark.valuesOfSingle  thrpt   10  123127.741 ± 2750.144  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<ValuesMapBenchmark>()
    }
}
