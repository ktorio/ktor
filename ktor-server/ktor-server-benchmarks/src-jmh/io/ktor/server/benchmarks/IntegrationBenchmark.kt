package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.jetty.*
import io.ktor.netty.*
import io.ktor.pipeline.*
import io.ktor.response.*
import io.ktor.routing.*
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
    private val largeFile = File("ktor-server/ktor-server-core/target/").walkTopDown().maxDepth(1).filter {
        it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
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

class NettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationHost {
        return embeddedServer(Netty, port, module = main)
    }
}

class JettyIntegrationBenchmark : IntegrationBenchmark() {
    override fun createServer(port: Int, main: Application.() -> Unit): ApplicationHost {
        return embeddedServer(Jetty, port, module = main)
    }
}

/*
Benchmark                                        Mode  Cnt   Score   Error   Units
JettyIntegrationBenchmark.jarfile               thrpt   20  13.894 ± 0.595  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   2.577 ± 0.099  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   2.143 ± 0.057  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  22.593 ± 0.930  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  36.612 ± 1.555  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  29.359 ± 0.890  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  27.205 ± 1.367  ops/ms
JettyIntegrationBenchmark.thinkOK               thrpt   20  37.203 ± 1.837  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  11.845 ± 0.724  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   2.210 ± 0.032  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   2.369 ± 0.034  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  17.456 ± 0.512  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  52.326 ± 1.057  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  24.794 ± 0.548  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  35.710 ± 0.781  ops/ms
NettyIntegrationBenchmark.thinkOK               thrpt   20  52.501 ± 0.699  ops/ms
*/

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 32
        run<NettyIntegrationBenchmark>()
        run<JettyIntegrationBenchmark>()
    }
}


