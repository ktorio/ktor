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
    fun sayOKLongParams() {
        load("http://localhost:$port/sayOK?utm_source=Yandex&utm_medium=cpc&utm_campaign=shuby_Ekb_search&utm_content=obshie&page=6")
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
CIOIntegrationBenchmark.jarfile                 thrpt   20  10.592 ± 0.192  ops/ms
CIOIntegrationBenchmark.largeFile               thrpt   20   0.535 ± 0.012  ops/ms
CIOIntegrationBenchmark.largeFileSync           thrpt   20   0.547 ± 0.016  ops/ms
CIOIntegrationBenchmark.regularClasspathFile    thrpt   20  15.654 ± 0.433  ops/ms
CIOIntegrationBenchmark.sayOK                   thrpt   20  56.161 ± 1.158  ops/ms
CIOIntegrationBenchmark.sayOKLongParams         thrpt   20  53.788 ± 0.833  ops/ms
CIOIntegrationBenchmark.smallFile               thrpt   20  30.727 ± 0.645  ops/ms
CIOIntegrationBenchmark.smallFileSync           thrpt   20  44.239 ± 0.792  ops/ms
CIOIntegrationBenchmark.thinkOK                 thrpt   20  54.354 ± 0.836  ops/ms

JettyIntegrationBenchmark.jarfile               thrpt   20  12.776 ± 0.570  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   1.171 ± 0.031  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   1.133 ± 0.021  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  17.985 ± 1.488  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  29.549 ± 1.770  ops/ms
JettyIntegrationBenchmark.sayOKLongParams       thrpt   20  26.048 ± 1.041  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  26.174 ± 2.346  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  26.058 ± 1.432  ops/ms
JettyIntegrationBenchmark.thinkOK               thrpt   20  28.185 ± 1.339  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  12.575 ± 0.161  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   0.919 ± 0.017  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   3.516 ± 0.036  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  18.771 ± 0.293  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  55.266 ± 1.751  ops/ms
NettyIntegrationBenchmark.sayOKLongParams       thrpt   20  53.322 ± 1.389  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  32.972 ± 0.730  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  44.912 ± 1.044  ops/ms
NettyIntegrationBenchmark.thinkOK               thrpt   20  53.655 ± 1.199  ops/ms

*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<JettyIntegrationBenchmark>()
        run<NettyIntegrationBenchmark>()
        run<CIOIntegrationBenchmark>()
    }
}


