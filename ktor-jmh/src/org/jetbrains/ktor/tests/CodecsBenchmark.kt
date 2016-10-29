package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.openjdk.jmh.annotations.*
import java.net.*

@State(Scope.Benchmark)
open class CodecsBenchmark {
    @Benchmark
    fun decodeHex() = decodeURLPart("%2A~%21%40%23%24%25%5E%26%28%29+%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D")

    @Benchmark
    fun decodePlain() = decodeURLPart("simple")

    @Benchmark
    fun decodeHexJava() = URLDecoder.decode("%2A~%21%40%23%24%25%5E%26%28%29+%7B%7D%22%5C%3B%3A%60%2C%2F%5B%5D".replace("+", "%2B"), Charsets.UTF_8.name())

    @Benchmark
    fun decodePlainJava() = URLDecoder.decode("simple", Charsets.UTF_8.name())
}

/*
CodecsBenchmark.decodeHex        thrpt   25    2695.780 ±   52.937  ops/ms
CodecsBenchmark.decodeHexJava    thrpt   25    1134.299 ±   18.629  ops/ms

CodecsBenchmark.decodePlain      thrpt   25  102850.462 ± 2158.148  ops/ms
CodecsBenchmark.decodePlainJava  thrpt   25   18082.128 ±  167.538  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<CodecsBenchmark>()
    }
}