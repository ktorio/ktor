package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.net.*


@State(Scope.Benchmark)
open class IntegrationBenchmark {
    private val packageName = FullBenchmark::class.java.`package`.name
    private val classFileName = FullBenchmark::class.simpleName!! + ".class"
    private val pomFile = File("pom.xml")

    lateinit private var server: NettyApplicationHost

    private val port = 5678

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level = Level.ERROR
        server = embeddedNettyServer(port) {
            routing {
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
        server.start()
    }

    private fun load(url: String): ByteArray {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            setRequestProperty("Accept-Encoding", "gzip")
        }.inputStream.readBytes()
    }

    @TearDown
    fun shutdownServer() {
        server.stop()
    }

    @Benchmark
    fun sayOK(): ByteArray {
        return load("http://localhost:$port/sayOK")
    }

    @Benchmark
    fun jarfile(): ByteArray {
        return load("http://localhost:$port/jarfile")
    }

    @Benchmark
    fun regularClasspathFile(): ByteArray {
        return load("http://localhost:$port/regularClasspathFile")
    }

    @Benchmark
    fun regularFile(): ByteArray {
        return load("http://localhost:$port/regularFile")
    }
}
/*
Benchmark                                   Mode  Cnt   Score   Error   Units
IntegrationBenchmark.jarfile               thrpt   10  11.359 ± 2.814  ops/ms
IntegrationBenchmark.regularClasspathFile  thrpt   10  14.951 ± 0.848  ops/ms
IntegrationBenchmark.regularFile           thrpt   10  15.070 ± 0.607  ops/ms
IntegrationBenchmark.sayOK                 thrpt   10  21.792 ± 3.005  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 1
        run<IntegrationBenchmark>()
    }
}


