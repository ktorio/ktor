package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.jetty.*
import org.jetbrains.ktor.netty.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.routing.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.util.concurrent.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark {
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File("pom.xml")
    private val largeFile = File("ktor-core/target/").walkTopDown().maxDepth(1).filter {
        it.name.startsWith("ktor-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit var server: ApplicationHost
    private val httpClient = OkHttpBenchmarkClient()

    private val port = 5678

    abstract fun createServer(port: Int, main: Application.() -> Unit): ApplicationHost

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
        httpClient.load(url).use {
            val buf = ByteArray(8192)
            while (it.read(buf) != -1);
        }
    }

    @Benchmark
    fun sayOK() {
        load("http://localhost:$port/sayOK")
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

open class NettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationHost {
        return embeddedServer(Netty, port, module = main)
    }
}

open class JettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationHost {
        return embeddedServer(Jetty, port, module = main)
    }
}

/*
Benchmark                                        Mode  Cnt   Score   Error   Units
JettyIntegrationBenchmark.jarfile               thrpt   20  15.788 ± 0.705  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   2.583 ± 0.058  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   2.245 ± 0.050  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  27.681 ± 0.420  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  52.410 ± 1.254  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  32.153 ± 1.782  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  35.807 ± 1.854  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  12.377 ± 0.605  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   2.286 ± 0.061  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   2.656 ± 0.047  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  18.381 ± 0.997  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  54.329 ± 1.887  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  27.238 ± 0.757  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  39.414 ± 1.668  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<NettyIntegrationBenchmark>()
        run<JettyIntegrationBenchmark>()
    }
}


