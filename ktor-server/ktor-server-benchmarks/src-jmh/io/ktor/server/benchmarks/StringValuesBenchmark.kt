package io.ktor.server.benchmarks

import io.ktor.http.*
import io.ktor.util.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
@UseExperimental(InternalAPI::class)
class StringValuesBenchmark {
    private val headers = valuesOf("A" to listOf("B"), "C" to listOf("D"))

    @Benchmark
    fun valuesOfSingle(): StringValues {
        return valuesOf("A", "B")
    }

    @Benchmark
    fun valuesOfMany(): StringValues {
        return valuesOf("A" to listOf("B"), "C" to listOf("D"))
    }

    @Benchmark
    fun build(): StringValues {
        return StringValues.build {
            append("A", "B")
            append("C", "D")
        }
    }

    @Benchmark
    fun filter(): StringValues {
        return headers.filter { _, _ -> true }
    }

    @Benchmark
    fun compression(): StringValues {
        return StringValues.build(true) {
            appendFiltered(headers) { name, _ -> !name.equals(HttpHeaders.ContentLength, true) }
            append(HttpHeaders.ContentEncoding, "deflate")
        }
    }
}

/*
Benchmark                           Mode  Cnt       Score      Error   Units
StringValuesBenchmark.build           thrpt   10   10062.523 ±  636.484  ops/ms
StringValuesBenchmark.compression     thrpt   10    4501.705 ±   73.952  ops/ms
StringValuesBenchmark.filter          thrpt   10    9073.771 ±  662.824  ops/ms
StringValuesBenchmark.valuesOfMany    thrpt   10   13795.421 ± 1234.157  ops/ms
StringValuesBenchmark.valuesOfSingle  thrpt   10  123127.741 ± 2750.144  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<StringValuesBenchmark>()
    }
}
