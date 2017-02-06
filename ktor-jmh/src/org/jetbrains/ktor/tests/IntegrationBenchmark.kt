package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.net.*


@State(Scope.Benchmark)
open class IntegrationBenchmark {
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File("pom.xml")
    private val largeFile = File("ktor-core/target/ktor-core-0.2.5-SNAPSHOT.jar")

    lateinit private var server: ApplicationHostStartable

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
                get("/smallFile") {
                    call.respond(LocalFileContent(smallFile))
                }
                get("/smallFileSync") {
                    call.respond(smallFile.readBytes())
                }
                get("/largeFile") {
                    call.respond(LocalFileContent(largeFile))
                }
                get("/largeFileSync") {
                    call.respond(largeFile.readBytes())
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
    fun smallFile(): ByteArray {
        return load("http://localhost:$port/smallFile")
    }

    @Benchmark
    fun smallFileSync(): ByteArray {
        return load("http://localhost:$port/smallFileSync")
    }
    
    @Benchmark
    fun largeFile(): ByteArray {
        return load("http://localhost:$port/largeFile")
    }

    @Benchmark
    fun largeFileSync(): ByteArray {
        return load("http://localhost:$port/largeFileSync")
    }
}
/*
Netty:

Benchmark                                   Mode  Cnt   Score   Error   Units
IntegrationBenchmark.jarfile               thrpt   10   9.972 ± 3.298  ops/ms
IntegrationBenchmark.largeFile             thrpt   10   0.867 ± 0.173  ops/ms
IntegrationBenchmark.largeFileSync         thrpt   10   1.465 ± 0.154  ops/ms
IntegrationBenchmark.regularClasspathFile  thrpt   10  14.976 ± 0.806  ops/ms
IntegrationBenchmark.sayOK                 thrpt   10  23.301 ± 1.221  ops/ms
IntegrationBenchmark.smallFile             thrpt   10  16.916 ± 1.150  ops/ms
IntegrationBenchmark.smallFileSync         thrpt   10  17.866 ± 4.305  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 4
        run<IntegrationBenchmark>()
    }
}


