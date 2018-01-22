package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.benchmarks.cio.*
import io.ktor.server.benchmarks.jetty.*
import io.ktor.server.benchmarks.netty.*
import io.ktor.server.benchmarks.test.*
import io.ktor.server.engine.*
import org.openjdk.jmh.annotations.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.util.concurrent.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark<TEngine : ApplicationEngine> {
    private val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File(coreDirectory, "build.gradle")
    private val largeFile = File(coreDirectory, "build").walkTopDown().maxDepth(2).filter {
                                it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
                            }.single()

    lateinit var server: TEngine
    private val httpClient = KtorBenchmarkClient()

    private val port = 5678

    abstract fun createServer(port: Int, main: Application.() -> Unit): TEngine
    protected open val localhost = "http://localhost:$port"

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level = Level.ERROR
        val okContent = TextContent("OK", ContentType.Text.Plain, HttpStatusCode.OK)
        server = createServer(port) {
            routing {
                get("/long/path/to/find/issues/with/routing/scalability") {
                    call.respond(okContent)
                }
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

    protected fun load(url: String) {
        httpClient.load(url)
    }

    @Benchmark
    fun random() {
        when (ThreadLocalRandom.current().nextInt(14)) {
            0, 1, 2, 3, 4 -> thinkOK()
            5, 6, 7, 8, 9 -> query()
            10, 11, 12 -> smallFile()
            13 -> largeFile()
        }
    }

    @Benchmark
    fun sayOK() {
        load("$localhost/sayOK")
    }

    @Benchmark
    fun longPath() {
        load("$localhost/long/path/to/find/issues/with/routing/scalability")
    }

    @Benchmark
    fun query() {
        load("$localhost/query?utm_source=Google&utm_medium=cpc&utm_campaign=ok%2B+plus&utm_content=obshie&message=OK")
    }

    @Benchmark
    fun thinkOK() {
        load("$localhost/thinkOK")
    }

    @Benchmark
    fun jarfile() {
        load("$localhost/jarfile")
    }

    @Benchmark
    fun regularClasspathFile() {
        load("$localhost/regularClasspathFile")
    }

    @Benchmark
    fun smallFile() {
        load("$localhost/smallFile")
    }

    @Benchmark
    fun smallFileSync() {
        load("$localhost/smallFileSync")
    }

    @Benchmark
    fun largeFile() {
        load("$localhost/largeFile")
    }

    @Benchmark
    fun largeFileSync() {
        load("$localhost/largeFileSync")
    }
}

/*
NOTE: Results on Ilya's MacBook Pro, rebooted without any extra programs running

$ gradle runBenchmark -PbenchmarkName=IntegrationBenchmark

Benchmark                                                      Mode  Cnt    Score    Error   Units
i.k.s.b.cio.CIOIntegrationBenchmark.jarfile                   thrpt   20    8.390 ±  0.270  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.largeFile                 thrpt   20    0.682 ±  0.014  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.largeFileSync             thrpt   20    0.696 ±  0.012  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.query                     thrpt   20   45.090 ±  0.874  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.random                    thrpt   20    8.111 ±  0.247  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.regularClasspathFile      thrpt   20   16.187 ±  0.264  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.sayOK                     thrpt   20   48.819 ±  0.891  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.smallFile                 thrpt   20   29.565 ±  2.076  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.smallFileSync             thrpt   20   37.043 ±  0.970  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.thinkOK                   thrpt   20   48.547 ±  0.707  ops/ms

i.k.s.b.jetty.JettyIntegrationBenchmark.jarfile               thrpt   20    8.609 ±  0.508  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.largeFile             thrpt   20    1.116 ±  0.028  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.largeFileSync         thrpt   20    1.411 ±  0.020  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.query                 thrpt   20   22.161 ±  1.391  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.random                thrpt   20    8.090 ±  0.351  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.regularClasspathFile  thrpt   20   14.452 ±  0.955  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.sayOK                 thrpt   20   26.201 ±  1.210  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.smallFile             thrpt   20   23.825 ±  2.450  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.smallFileSync         thrpt   20   23.818 ±  0.972  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.thinkOK               thrpt   20   26.111 ±  1.179  ops/ms

i.k.s.b.netty.NettyIntegrationBenchmark.jarfile               thrpt   20    8.945 ±  0.250  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.largeFile             thrpt   20    0.770 ±  0.031  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.largeFileSync         thrpt   20    3.225 ±  0.065  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.query                 thrpt   20   42.816 ±  0.661  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.random                thrpt   20    7.922 ±  0.201  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.regularClasspathFile  thrpt   20   17.128 ±  0.448  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.sayOK                 thrpt   20   47.304 ±  1.055  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.smallFile             thrpt   20   30.102 ±  0.928  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.smallFileSync         thrpt   20   36.826 ±  0.650  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.thinkOK               thrpt   20   45.584 ±  0.758  ops/ms

i.k.s.b.test.TestIntegrationBenchmark.jarfile                 thrpt   20   15.871 ±  0.186  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.largeFile               thrpt   20    2.955 ±  0.034  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.largeFileSync           thrpt   20    2.904 ±  0.030  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.query                   thrpt   20  550.201 ±  6.534  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.random                  thrpt   20   36.724 ±  0.645  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.regularClasspathFile    thrpt   20   48.553 ±  1.083  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.sayOK     e              thrpt   20  839.996 ± 12.154  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.smallFile               thrpt   20   87.324 ±  2.540  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.smallFileSync           thrpt   20  146.581 ±  4.622  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.thinkOK                 thrpt   20  805.347 ± 11.841  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<CIOIntegrationBenchmark>()
        run<JettyIntegrationBenchmark>()
        run<NettyIntegrationBenchmark>()
        run<TestIntegrationBenchmark>()
    }
}


