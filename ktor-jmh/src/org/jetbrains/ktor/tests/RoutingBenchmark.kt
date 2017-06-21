package org.jetbrains.ktor.tests

import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.openjdk.jmh.annotations.*
import java.util.concurrent.*

@State(Scope.Benchmark)
open class RoutingBenchmark {
    private val testHost: TestApplicationHost = TestApplicationHost(createTestEnvironment())

    @Setup
    fun startServer() {
        testHost.start()
        testHost.application.routing {
            get("/short") {
                call.respond("short")
            }
            get("/plain/path/with/multiple/components") {
                call.respond("long")
            }
            get("/plain/{path}/with/parameters/components") {
                call.respond("param ${call.parameters["path"] ?: "Fail"}")
            }
        }
    }

    @TearDown
    fun stopServer() {
        testHost.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }

    @Benchmark
    fun shortPath() = handle("/short") {
        check(response.content == "short") { "Invalid response" }
    }

    @Benchmark
    fun longPath() = handle("/plain/path/with/multiple/components") {
        check(response.content == "long") { "Invalid response" }
    }

    @Benchmark
    fun paramPath() = handle("/plain/OK/with/parameters/components") {
        check(response.content == "param OK") { "Invalid response" }
    }

    private inline fun <R> handle(url: String, block: TestApplicationCall.() -> R) = testHost.handleRequest(HttpMethod.Get, url).apply {
        if (response.status() != HttpStatusCode.OK) {
            throw IllegalStateException("wrong response code")
        }

        block()
    }
}

/*
Benchmark                    Mode  Cnt     Score    Error   Units
RoutingBenchmark.longPath   thrpt   20   872.538 ± 26.187  ops/ms
RoutingBenchmark.paramPath  thrpt   20   814.574 ± 15.689  ops/ms
RoutingBenchmark.shortPath  thrpt   20  1022.062 ± 18.937  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<RoutingBenchmark>()
    }
}