package org.jetbrains.ktor.tests

import ch.qos.logback.classic.Level
import org.apache.http.client.methods.*
import org.apache.http.impl.client.*
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
import java.net.*
import java.util.concurrent.*


@State(Scope.Benchmark)
abstract class IntegrationBenchmark {
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File("pom.xml")
    private val largeFile = File("ktor-core/target/").walkTopDown().maxDepth(1).filter {
        it.name.startsWith("ktor-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit private var server: ApplicationHostStartable
    private var httpClient: CloseableHttpClient? = null
    private val useApacheClient = false

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
        if (useApacheClient) {
            val builder = HttpClientBuilder.create()
            httpClient = builder.build()
        }

    }

    @TearDown
    fun shutdownClient() {
        if (useApacheClient) {
            httpClient!!.close()
            httpClient = null
        }
    }

    private fun load(url: String) {
        val inputStream = if (useApacheClient) {
            val httpGet = HttpGet(url)
            val response = httpClient!!.execute(httpGet)
            response.entity.content
        } else {
            (URL(url).openConnection() as HttpURLConnection).apply {
                // setRequestProperty("Connection", "close")
                setRequestProperty("Accept-Encoding", "gzip")
            }.inputStream
        }
        inputStream.use {
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
JettyIntegrationBenchmark.jarfile               thrpt   10  12.509 ± 2.196  ops/ms
JettyIntegrationBenchmark.largeFile             thrpt   10   1.021 ± 0.089  ops/ms
JettyIntegrationBenchmark.regularClasspathFile  thrpt   10  15.423 ± 1.317  ops/ms
JettyIntegrationBenchmark.sayOK                 thrpt   10  25.281 ± 2.988  ops/ms
JettyIntegrationBenchmark.smallFile             thrpt   10  17.042 ± 1.622  ops/ms

Benchmark                                        Mode  Cnt   Score   Error   Units
NettyIntegrationBenchmark.jarfile               thrpt   10   9.104 ± 2.282  ops/ms
NettyIntegrationBenchmark.largeFile             thrpt   10   2.393 ± 0.314  ops/ms
NettyIntegrationBenchmark.largeFileSync         thrpt   10   2.676 ± 0.227  ops/ms
NettyIntegrationBenchmark.regularClasspathFile  thrpt   10  16.470 ± 3.606  ops/ms
NettyIntegrationBenchmark.sayOK                 thrpt   10  35.345 ± 4.163  ops/ms
NettyIntegrationBenchmark.smallFile             thrpt   10  25.252 ± 5.469  ops/ms
NettyIntegrationBenchmark.smallFileSync         thrpt   10  29.195 ± 6.683  ops/ms
*/

fun main(args: Array<String>) {
    if (args.firstOrNull() == "daemon") {
        NettyIntegrationBenchmark().configureServer()
    } else
    benchmark(args) {
        threads = 32
        run<NettyIntegrationBenchmark>()
        //run<JettyIntegrationBenchmark>()
    }
}


