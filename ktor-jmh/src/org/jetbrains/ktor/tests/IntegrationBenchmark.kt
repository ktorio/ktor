package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
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

    lateinit var server: ApplicationHostStartable
    private val httpClient = OkHttpBenchmarkClient()

    private val port = 5678

    abstract fun createServer(port: Int, configure: Application.() -> Unit): ApplicationHostStartable

    @Setup
    fun configureServer() {
        val root = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as ch.qos.logback.classic.Logger
        root.level = Level.ERROR
        server = createServer(port) {
            routing {
                get("/sayOK") {
                    call.respondText("OK")
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
JettyIntegrationBenchmark.jarfile               thrpt   20  12.966 ± 1.676  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   20   2.352 ± 0.110  ops/ms
JettyIntegrationBenchmark.largeFileSync         thrpt   20   1.953 ± 0.111  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   20  18.118 ± 0.568  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   20  27.796 ± 1.445  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   20  20.637 ± 0.765  ops/ms
JettyIntegrationBenchmark.smallFileSync         thrpt   20  21.408 ± 1.150  ops/ms

NettyIntegrationBenchmark.jarfile               thrpt   20  10.107 ± 0.729  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   20   1.871 ± 0.101  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   20   2.201 ± 0.067  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   20  15.126 ± 0.985  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   20  43.506 ± 1.765  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   20  21.542 ± 0.566  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   20  30.185 ± 3.666  ops/ms*/

fun main(args: Array<String>) {
    if (args.firstOrNull() == "daemon") {
        NettyIntegrationBenchmark().configureServer()
    } else
    benchmark(args) {
        threads = 32
        run<NettyIntegrationBenchmark>()
        run<JettyIntegrationBenchmark>()
    }
}


