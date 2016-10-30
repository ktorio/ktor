package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.openjdk.jmh.annotations.*

@State(Scope.Benchmark)
open class RoutingBenchmark {
    private val testHost: TestApplicationHost = createTestHost()
    @Setup
    fun configureRouting() {
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
        await()

        if (response.status() != HttpStatusCode.OK) {
            throw IllegalStateException("wrong response code")
        }

        block()
    }
}

/*
RoutingBenchmark.longPath   thrpt   10  119.836 ± 13.525  ops/ms
RoutingBenchmark.paramPath  thrpt   10  106.218 ± 11.797  ops/ms
RoutingBenchmark.shortPath  thrpt   10  132.592 ±  5.171  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<RoutingBenchmark>()
    }
}