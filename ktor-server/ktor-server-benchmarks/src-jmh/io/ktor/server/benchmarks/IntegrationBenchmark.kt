package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.http.content.*
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
    private val httpClient = OkHttpBenchmarkClient()

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

Benchmark                                                      Mode  Cnt    Score   Error   Units
i.k.s.b.cio.CIOIntegrationBenchmark.jarfile                   thrpt   20   10.217 ± 0.452  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.largeFile                 thrpt   20    1.064 ± 0.026  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.largeFileSync             thrpt   20    0.645 ± 0.017  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.longPath                  thrpt   20   49.213 ± 0.588  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.query                     thrpt   20   45.203 ± 0.902  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.random                    thrpt   20   11.531 ± 0.469  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.regularClasspathFile      thrpt   20   18.748 ± 0.336  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.sayOK                     thrpt   20   50.870 ± 0.784  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.smallFile                 thrpt   20   32.664 ± 0.534  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.smallFileSync             thrpt   20   37.114 ± 0.696  ops/ms
i.k.s.b.cio.CIOIntegrationBenchmark.thinkOK                   thrpt   20   51.270 ± 0.785  ops/ms

i.k.s.b.jetty.JettyIntegrationBenchmark.jarfile               thrpt   20   11.181 ± 0.457  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.largeFile             thrpt   20    1.812 ± 0.041  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.largeFileSync         thrpt   20    2.550 ± 0.139  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.longPath              thrpt   20   35.177 ± 2.778  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.query                 thrpt   20   27.416 ± 2.841  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.random                thrpt   20   12.948 ± 0.623  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.regularClasspathFile  thrpt   20   14.755 ± 1.247  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.sayOK                 thrpt   20   31.445 ± 1.872  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.smallFile             thrpt   20   26.449 ± 2.458  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.smallFileSync         thrpt   20   27.044 ± 2.389  ops/ms
i.k.s.b.jetty.JettyIntegrationBenchmark.thinkOK               thrpt   20   30.741 ± 2.458  ops/ms

i.k.s.b.netty.NettyIntegrationBenchmark.jarfile               thrpt   20   12.470 ± 0.445  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.largeFile             thrpt   20    2.189 ± 0.062  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.largeFileSync         thrpt   20    3.564 ± 0.065  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.longPath              thrpt   20   45.505 ± 1.166  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.query                 thrpt   20   43.703 ± 0.778  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.random                thrpt   20   17.386 ± 0.353  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.regularClasspathFile  thrpt   20   21.320 ± 0.361  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.sayOK                 thrpt   20   48.073 ± 0.894  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.smallFile             thrpt   20   30.410 ± 0.393  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.smallFileSync         thrpt   20   36.090 ± 0.561  ops/ms
i.k.s.b.netty.NettyIntegrationBenchmark.thinkOK               thrpt   20   47.290 ± 1.024  ops/ms

i.k.s.b.test.TestIntegrationBenchmark.jarfile                 thrpt   20   18.978 ± 0.275  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.largeFile               thrpt   20    2.447 ± 0.016  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.largeFileSync           thrpt   20    3.024 ± 0.014  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.longPath                thrpt   20  162.994 ± 2.890  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.query                   thrpt   20  146.176 ± 2.389  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.random                  thrpt   20   25.105 ± 0.574  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.regularClasspathFile    thrpt   20   31.574 ± 0.847  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.sayOK                   thrpt   20  169.702 ± 2.615  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.smallFile               thrpt   20   51.168 ± 1.337  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.smallFileSync           thrpt   20   78.312 ± 4.669  ops/ms
i.k.s.b.test.TestIntegrationBenchmark.thinkOK                 thrpt   20  166.843 ± 2.513  ops/ms
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


