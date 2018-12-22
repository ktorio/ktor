package io.ktor.server.benchmarks

import io.ktor.http.*
import org.openjdk.jmh.annotations.*
import java.net.*

@State(Scope.Benchmark)
class CodecsBenchmark {
    @Benchmark
    fun decodeHex() = "%2A~%21%40%23%24%25%5E%26%28%29+%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D".decodeURLPart()

    @Benchmark
    fun decodePlain() = "simple".decodeURLPart()

    @Benchmark
    fun decodeHexJava() = URLDecoder.decode(
        "%2A~%21%40%23%24%25%5E%26%28%29+%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D".replace("+", "%2B")
        , Charsets.UTF_8.name()
    )

    @Benchmark
    fun decodePlainJava() = URLDecoder.decode("simple", Charsets.UTF_8.name())
}

/*
CodecsBenchmark.decodeHex        thrpt   10    3772.046 ±   76.073  ops/ms
CodecsBenchmark.decodeHexJava    thrpt   10    1144.964 ±   20.379  ops/ms
CodecsBenchmark.decodePlain      thrpt   10  225807.808 ± 5109.027  ops/ms
CodecsBenchmark.decodePlainJava  thrpt   10   16731.668 ±  352.700  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<CodecsBenchmark>()
    }
}