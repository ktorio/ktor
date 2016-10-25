package org.jetbrains.ktor.tests.full

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.jetbrains.ktor.tests.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.runner.*
import org.openjdk.jmh.runner.options.*
import java.util.concurrent.*


@State(Scope.Benchmark)
open class FullBenchmark {
    private val testHost: TestApplicationHost = createTestHost()
    private val classSignature =  listOf(0xca, 0xfe, 0xba, 0xbe).map(Int::toByte)

    @Setup
    fun configureServer() {
        testHost.application.routing {
            get("/sayOK") {
                call.respond("OK")
            }
            get("/jarfile") {
                call.respond(call.resolveClasspathWithPath("java/lang/", "String.class")!!)
            }
        }
    }

    @Benchmark
    fun sayOK() = handle("/sayOK") {
        if (response.content != "OK") {
            throw IllegalStateException()
        }
    }

    @Benchmark
    fun sayClasspathResourceFromJar() = handle("/jarfile") {
        if (response.byteContent!!.take(4) != classSignature) {
            throw IllegalStateException("Wrong class signature")
        }
    }

    private inline fun <R> handle(url: String, block: TestApplicationCall.() -> R) = testHost.handleRequest(HttpMethod.Get, url).apply {
        await()

        if (response.status() != HttpStatusCode.OK) {
            throw IllegalStateException("wrong response code")
        }

        block()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = OptionsBuilder()
                    .mode(Mode.AverageTime)
                    .timeUnit(TimeUnit.MICROSECONDS)
                    .include(FullBenchmark::class.java.name)
                    .warmupIterations(7)
                    .measurementIterations(25)
                    .measurementTime(TimeValue.milliseconds(500))
                    .forks(1)

            Runner(options.build()).run()

//            FullBenchmark().apply {
//                configureServer()
//
//                while (true) {
//                    sayClasspathResourceFromJar()
//                }
//            }
        }
    }
}
