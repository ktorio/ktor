package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.routing.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.net.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark {
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File("pom.xml")
    private val largeFile = File("ktor-core/target/").walkTopDown().maxDepth(1).filter {
        it.name.startsWith("ktor-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit private var server: ApplicationHostStartable

    private val port = 5678

    abstract fun createServer(port: Int, configure: Application.() -> Unit): ApplicationHostStartable

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level = Level.ERROR
        server = createServer(port) {
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

open class NettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, configure: Application.() -> Unit): ApplicationHostStartable {
        return embeddedNettyServer(port, configure = configure)
    }
}

open class JettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, configure: Application.() -> Unit): ApplicationHostStartable {
        return embeddedJettyServer(port, configure = configure)
    }
}

/*
Benchmark                                        Mode  Cnt   Score   Error   Units
JettyIntegrationBenchmark.jarfile               thrpt   10  12.509 ± 2.196  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   10   1.021 ± 0.089  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   10  15.423 ± 1.317  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   10  25.281 ± 2.988  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   10  17.042 ± 1.622  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   10   9.374 ± 2.783  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   10   0.525 ± 0.021  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   10  11.799 ± 3.637  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   10  18.154 ± 7.519  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   10  12.484 ± 3.729  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 4
        run<NettyIntegrationBenchmark>()
        run<JettyIntegrationBenchmark>()
    }
}


