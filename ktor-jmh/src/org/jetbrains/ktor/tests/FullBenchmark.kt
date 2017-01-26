package org.jetbrains.ktor.tests

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.testing.*
import org.openjdk.jmh.annotations.*
import java.io.*


@State(Scope.Benchmark)
open class FullBenchmark {
    private val testHost: TestApplicationHost = createTestHost()
    private val classSignature = listOf(0xca, 0xfe, 0xba, 0xbe).map(Int::toByte)
    private val packageName = FullBenchmark::class.java.`package`.name
    private val classFileName = FullBenchmark::class.simpleName!! + ".class"
    private val pomFile = File("pom.xml")

    @Setup
    fun configureServer() {
        testHost.application.routing {
            get("/sayOK") {
                call.respond("OK")
            }
            get("/jarfile") {
                call.respond(call.resolveClasspathWithPath("java/lang/", "String.class")!!)
            }
            get("/regularClasspathFile") {
                call.respond(call.resolveClasspathWithPath(packageName, classFileName)!!)
            }
            get("/regularFile") {
                call.respond(LocalFileContent(pomFile))
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

    @Benchmark
    fun sayClasspathResourceRegular() = handle("/regularClasspathFile") {
        if (response.byteContent!!.take(4) != classSignature) {
            throw IllegalStateException("Wrong class signature")
        }
    }

    @Benchmark
    fun sayRegularFile() = handle("/regularFile") {
        response.byteContent
    }

    private inline fun <R> handle(url: String, block: TestApplicationCall.() -> R) = testHost.handleRequest(HttpMethod.Get, url).apply {
        if (response.status() != HttpStatusCode.OK) {
            throw IllegalStateException("wrong response code")
        }

        block()
    }
}

/*
FullBenchmark.sayClasspathResourceFromJar  thrpt   10   23.610 ± 1.580  ops/ms
FullBenchmark.sayClasspathResourceRegular  thrpt   10   15.055 ± 0.215  ops/ms
FullBenchmark.sayOK                        thrpt   10  138.655 ± 2.271  ops/ms
FullBenchmark.sayRegularFile               thrpt   10   24.001 ± 0.254  ops/ms
 */

fun main(args: Array<String>) {
    benchmark(args) {
        run<FullBenchmark>()
    }
}
