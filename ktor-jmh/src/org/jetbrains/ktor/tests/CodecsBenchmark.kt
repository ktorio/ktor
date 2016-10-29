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
CodecsBenchmark.decodeHex        thrpt   25    2565.009 ±  121.976  ops/ms
CodecsBenchmark.decodeHexJava    thrpt   25    1172.826 ±   30.980  ops/ms

CodecsBenchmark.decodePlain      thrpt   25  143535.384 ± 1981.501  ops/ms
CodecsBenchmark.decodePlainJava  thrpt   25   16704.193 ±  234.256  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<CodecsBenchmark>()
    }
}