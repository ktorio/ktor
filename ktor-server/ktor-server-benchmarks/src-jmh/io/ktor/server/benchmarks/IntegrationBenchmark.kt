package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.ktor.server.netty.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.util.concurrent.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark {
    private val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File(coreDirectory, "build.gradle")
    private val largeFile = File(coreDirectory, "build").walkTopDown().maxDepth(2).filter {
        it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit var server: ApplicationEngine
    private val httpClient = OkHttpBenchmarkClient()

    private val port = 5678

    abstract fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        val okContent = TextContent("OK", ContentType.Text.Plain, HttpStatusCode.OK)
        root.level = Level.ERROR
        server = createServer(port) {
            routing {
                get("/sayOK") {
                    call.respond(okContent)
                }
                get("/thinkOK") {
                    call.respondText("OK")
                }
                get("/query") {
                    val parameters = call.parameters
                    val message = parameters["message"]
                            ?: throw IllegalArgumentException("GET request should have `message` parameter")
                    call.respondText(message)
                }
                static {
                    resource("jarfile", "String.class", "java.lang")
                    resource("regularClasspathFile", classFileName, packageName)
                    file("smallFile", smallFile)
                    file("largeFile", largeFile)
                }
                get("/smallFileSync") {
                    call.respond(smallFile.readBytes())
                }
                get("/largeFileSync") {
                    call.respond(largeFile.readBytes())
                }
            }
        }
        server.start()
        Thread.sleep(500)
    }

    @TearDown
    fun shutdownServer() {
        server.stop(100, 5000, TimeUnit.MILLISECONDS)
    }

    @Setup
    fun configureClient() {
        httpClient.setup()
    }

    @TearDown
    fun shutdownClient() {
        httpClient.shutdown()
    }

    private fun load(url: String) {
        httpClient.load(url)
    }

    @Benchmark
    fun sayOK() {
        load("http://localhost:$port/sayOK")
    }

    @Benchmark
    fun query() {
        load("http://localhost:$port/query?utm_source=Google&utm_medium=cpc&utm_campaign=ok%2B+plus&utm_content=obshie&message=OK")
    }

    @Benchmark
    fun thinkOK() {
        load("http://localhost:$port/thinkOK")
    }

    @Benchmark
    fun jarfile() {
        load("http://localhost:$port/jarfile")
    }

    @Benchmark
    fun regularClasspathFile() {
        load("http://localhost:$port/regularClasspathFile")
    }

    @Benchmark
    fun smallFile() {
        load("http://localhost:$port/smallFile")
    }

    @Benchmark
    fun smallFileSync() {
        load("http://localhost:$port/smallFileSync")
    }

    @Benchmark
    fun largeFile() {
        load("http://localhost:$port/largeFile")
    }

    @Benchmark
    fun largeFileSync() {
        load("http://localhost:$port/largeFileSync")
    }
}

class NettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(Netty, port, module = main)
    }
}

class JettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(Jetty, port, module = main)
    }
}

class CIOIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationEngine {
        return embeddedServer(CIO, port, module = main)
    }
}

/*
NOTE: Results on Ilya's MacBook Pro, rebooted without any extra programs running, executed with Gradle

Benchmark                                        Mode  Cnt   Score   Error   Units
CIOIntegrationBenchmark.jarfile                 thrpt   20   8.280 ± 0.314  ops/ms
CIOIntegrationBenchmark.largeFile               thrpt   20   0.516 ± 0.011  ops/ms
CIOIntegrationBenchmark.largeFileSync           thrpt   20   0.570 ± 0.016  ops/ms
CIOIntegrationBenchmark.query                   thrpt   20  47.998 ± 1.252  ops/ms
CIOIntegrationBenchmark.regularClasspathFile    thrpt   20  15.109 ± 0.255  ops/ms
CIOIntegrationBenchmark.sayOK                   thrpt   20  53.502 ± 1.659  ops/ms
CIOIntegrationBenchmark.smallFile               thrpt   20  31.820 ± 0.476  ops/ms
CIOIntegrationBenchmark.smallFileSync           thrpt   20  42.918 ± 1.017  ops/ms
CIOIntegrationBenchmark.thinkOK                 thrpt   20  54.000 ± 0.777  ops/ms

JettyIntegrationBenchmark.jarfile               thrpt   20   9.241 ± 0.467  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   1.121 ± 0.032  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   1.181 ± 0.025  ops/ms
JettyIntegrationBenchmark.query                 thrpt   20  27.000 ± 1.867  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  16.778 ± 1.675  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  30.925 ± 1.612  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  26.338 ± 1.829  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  25.525 ± 1.588  ops/ms
JettyIntegrationBenchmark.thinkOK               thrpt   20  27.348 ± 2.219  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  10.640 ± 0.234  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   0.853 ± 0.015  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   3.633 ± 0.063  ops/ms
NettyIntegrationBenchmark.query                 thrpt   20  47.397 ± 0.811  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  18.127 ± 0.294  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  51.893 ± 1.016  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  34.043 ± 0.747  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  42.729 ± 1.431  ops/ms
NettyIntegrationBenchmark.thinkOK               thrpt   20  51.209 ± 1.335  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<JettyIntegrationBenchmark>()
        run<NettyIntegrationBenchmark>()
        run<CIOIntegrationBenchmark>()
    }
}


