package io.ktor.server.benchmarks

import ch.qos.logback.classic.Level
import io.ktor.application.*
import io.ktor.client.engine.cio.*
import io.ktor.http.content.*
import io.ktor.http.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.benchmarks.cio.*
import io.ktor.server.benchmarks.jetty.*
import io.ktor.server.benchmarks.netty.*
import io.ktor.server.engine.*
import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.*
import org.slf4j.*
import org.slf4j.Logger
import java.io.*
import java.util.concurrent.*

@State(Scope.Group)
abstract class AsyncIntegrationBenchmark<TEngine : ApplicationEngine> {
    private val coreDirectory = File("../ktor-server-core").absoluteFile.normalize()
    private val packageName = IntegrationBenchmark::class.java.`package`.name
    private val classFileName = IntegrationBenchmark::class.simpleName!! + ".class"
    private val smallFile = File(coreDirectory, "build.gradle")
    private val largeFile = File(coreDirectory, "build").walkTopDown().maxDepth(2).filter {
        it.name.startsWith("ktor-server-core") && it.name.endsWith("SNAPSHOT.jar")
    }.single()

    lateinit var server: TEngine
    protected val httpClient = KtorBenchmarkClient(CIO)

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

    fun load(url: String) = httpClient.submitTask(url)
    fun join(control: Control) = httpClient.joinTask(control)

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

    @Benchmark
    @Group("sayOKGroup")
    fun sayOKLoad() = load("$localhost/sayOK")

    @Benchmark
    @Group("longPathGroup")
    fun longPathLoad() = load("$localhost/long/path/to/find/issues/with/routing/scalability")

    @Benchmark
    @Group("queryGroup")
    fun queryLoad() = load("$localhost/query?utm_source=Google&utm_medium=cpc&utm_campaign=ok%2B+plus&utm_content=obshie&message=OK")

    @Benchmark
    @Group("thinkOKGroup")
    fun thinkOKLoad() = load("$localhost/thinkOK")

    @Benchmark
    @Group("jarfileGroup")
    fun jarfileLoad() = load("$localhost/jarfile")

    @Benchmark
    @Group("regularClasspathFileGroup")
    fun regularClasspathFileLoad() = load("$localhost/regularClasspathFile")

    @Benchmark
    @Group("smallFileGroup")
    fun smallFileLoad() = load("$localhost/smallFile")

    @Benchmark
    @Group("smallFileSyncGroup")
    fun smallFileSyncLoad() = load("$localhost/smallFileSync")

    @Benchmark
    @Group("largeFileGroup")
    fun largeFileLoad() = load("$localhost/largeFile")

    @Benchmark
    @Group("largeFileSyncGroup")
    fun largeFileSyncLoad() = load("$localhost/largeFileSync")

    @Benchmark
    @Group("sayOKGroup")
    fun sayOK(control: Control) = join(control)

    @Benchmark
    @Group("longPathGroup")
    fun longPath(control: Control) = join(control)

    @Benchmark
    @Group("queryGroup")
    fun query(control: Control) = join(control)

    @Benchmark
    @Group("thinkOKGroup")
    fun thinkOk(control: Control) = join(control)

    @Benchmark
    @Group("jarfileGroup")
    fun jarfile(control: Control) = join(control)

    @Benchmark
    @Group("regularClasspathFileGroup")
    fun regularClasspathFile(control: Control) = join(control)

    @Benchmark
    @Group("smallFileGroup")
    fun smallFile(control: Control) = join(control)

    @Benchmark
    @Group("largeFileGroup")
    fun largeFile(control: Control) = join(control)

    @Benchmark
    @Group("largeFileSyncGroup")
    fun largeFileSync(control: Control) = join(control)
}

fun main(args: Array<String>) {
    benchmark(args) {
        threads = 2
        run<CIOAsyncIntegrationBenchmark>()
        run<JettyAsyncIntegrationBenchmark>()
        run<NettyAsyncIntegrationBenchmark>()
    }
}
