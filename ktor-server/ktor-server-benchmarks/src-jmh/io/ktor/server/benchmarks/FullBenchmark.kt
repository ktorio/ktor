package io.ktor.server.benchmarks

import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.testing.*
import org.openjdk.jmh.annotations.*
import java.io.*
import java.util.concurrent.*


@State(Scope.Benchmark)
class FullBenchmark {
    private val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private val testHost: TestApplicationEngine = TestApplicationEngine(createTestEnvironment())
    private val classSignature = listOf(0xca, 0xfe, 0xba, 0xbe).map(Int::toByte)
    private val packageName = FullBenchmark::class.java.`package`.name
    private val classFileName = FullBenchmark::class.simpleName!! + ".class"
    private val smallFile = File(coreDirectory, "build.gradle")

    @Setup
    fun startServer() {
        testHost.start()
        testHost.application.routing {
            get("/sayOK") {
                call.parameters // force lazy initialization
                call.respond("OK")
            }
            get("/jarfile") {
                val resource = call.resolveResource("String.class", "java.lang")!!
                call.respond(resource)
            }
            get("/regularClasspathFile") {
                val resource = call.resolveResource(classFileName, packageName)!!
                call.respond(resource)
            }
            get("/regularFile") {
                call.respond(LocalFileContent(smallFile))
            }
        }
    }

    @TearDown
    fun stopServer() {
        testHost.stop(0L, 0L, TimeUnit.MILLISECONDS)
    }

    @Benchmark
    fun sayOK() = handle("/sayOK") {
        if (response.content != "OK") {
            throw IllegalStateException()
        }
    }

    @Benchmark
    fun sayOKLongParams() = handle("/sayOK?utm_source=Yandex&utm_medium=cpc&utm_campaign=shuby_Ekb_search&utm_content=obshie&page=6") {
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
            throw IllegalStateException("Expected 'HttpStatusCode.OK' but got '${response.status()}'")
        }

        block()
    }
}

/*
Benchmark                                   Mode  Cnt    Score    Error   Units
FullBenchmark.sayClasspathResourceFromJar  thrpt   20   29.682 ±  0.355  ops/ms
FullBenchmark.sayClasspathResourceRegular  thrpt   20   65.846 ±  0.929  ops/ms
FullBenchmark.sayOK                        thrpt   20  970.297 ± 13.466  ops/ms
FullBenchmark.sayOKLongParams              thrpt   20  562.497 ±  6.192  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        run<FullBenchmark>()
    }
}
