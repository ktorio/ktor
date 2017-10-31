package io.ktor.server.testing

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.backend.*
import io.ktor.client.backend.apache.*
import io.ktor.client.backend.jetty.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.response.*
import io.ktor.features.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.util.*
import kotlinx.coroutines.experimental.*
import org.junit.*
import org.junit.rules.*
import org.junit.runners.model.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.security.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.test.*


abstract class EngineTestBase<TEngine : ApplicationEngine, TConfiguration : ApplicationEngine.Configuration>(val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>) {
    protected val isUnderDebugger = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.orEmpty().any { "-agentlib:jdwp" in it }
    protected var port = findFreePort()
    protected var sslPort = findFreePort()
    protected var server: TEngine? = null
    protected var callGroupSize = -1
        private set
    protected val exceptions = ArrayList<Throwable>()
    protected var enableHttp2: Boolean = System.getProperty("enable.http2") == "true"
    protected var enableSsl: Boolean = System.getProperty("enable.ssl") != "false"

    private val allConnections = CopyOnWriteArrayList<HttpURLConnection>()

    val testLog: Logger = LoggerFactory.getLogger("EngineTestBase")

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class Http2Only

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class NoHttp2

    @get:Rule
    val test = TestName()

    @get:Rule
    open val timeout = PublishedTimeout(if (isUnderDebugger) 1000000L else (System.getProperty("host.test.timeout.seconds")?.toLong() ?: 120L))

    protected val socketReadTimeout by lazy { timeout.seconds.toInt() * 1000 }

    @Before
    fun setUpBase() {
        val method = this.javaClass.getMethod(test.methodName) ?: fail("Method ${test.methodName} not found")

        if (method.isAnnotationPresent(Http2Only::class.java)) {
            Assume.assumeTrue("http2 is not enabled", enableHttp2)
        }
        if (method.isAnnotationPresent(NoHttp2::class.java)) {
            enableHttp2 = false
        }

        if (enableHttp2) {
            Class.forName("sun.security.ssl.ALPNExtension", true, null)
        }

        testLog.trace("Starting server on port $port (SSL $sslPort)")
        exceptions.clear()
    }

    @After
    fun tearDownBase() {
        allConnections.forEach { it.disconnect() }
        testLog.trace("Disposing server on port $port (SSL $sslPort)")
        (server as? ApplicationEngine)?.stop(1000, 5000, TimeUnit.MILLISECONDS)
        if (exceptions.isNotEmpty()) {
            fail("Server exceptions logged, consult log output for more information")
        }
    }

    protected open fun createServer(log: Logger?, module: Application.() -> Unit): TEngine {
        val _port = this.port
        val environment = applicationEngineEnvironment {
            val delegate = LoggerFactory.getLogger("ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(msg: String?, t: Throwable?) {
                    t?.let { exceptions.add(it) }
                    delegate.error(msg, t)
                }
            }

            connector { port = _port }
            if (enableSsl) {
                sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                    this.port = sslPort
                    this.keyStorePath = keyStoreFile.absoluteFile
                }
            }

            module(module)
        }

        return embeddedServer(applicationEngineFactory, environment) {
            this@EngineTestBase.callGroupSize = callGroupSize
        }
    }

    protected open fun features(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(CallLogging)
        application.install(Routing, routingConfigurer)
    }

    protected fun createAndStartServer(log: Logger? = null, routingConfigurer: Routing.() -> Unit): TEngine {
        var lastFailures = emptyList<Throwable>()
        for (attempt in 1..5) {
            val server = createServer(log) {
                features(this, routingConfigurer)
            }

            val failures = startServer(server)
            if (failures.isEmpty()) {
                return server
            } else if (failures.any { it is BindException }) {
                port = findFreePort()
                sslPort = findFreePort()
                server.stop(1L, 1L, TimeUnit.SECONDS)
                lastFailures = failures
            } else {
                server.stop(1L, 1L, TimeUnit.SECONDS)
                throw MultipleFailureException(failures)
            }
        }

        throw MultipleFailureException(lastFailures)
    }

    private fun startServer(server: TEngine): List<Throwable> {
        this.server = server

        val l = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()

        val starting = launch(CommonPool + CoroutineExceptionHandler { _, _ -> }) {
            l.countDown()
            server.start()
        }
        l.await()

        val waitForPorts = launch(CommonPool) {
            server.environment.connectors.forEach { connector ->
                waitForPort(connector.port)
            }
        }

        starting.invokeOnCompletion { t ->
            if (t != null) {
                failures.add(t)
                waitForPorts.cancel()
            }
        }

        runBlocking {
            waitForPorts.join()
        }

        return failures
    }

    protected fun findFreePort() = ServerSocket(0).use { it.localPort }
    protected fun withUrl(path: String, builder: HttpRequestBuilder.() -> Unit = {}, block: suspend HttpResponse.(Int) -> Unit) {
        withUrl(URL("http://127.0.0.1:$port$path"), port, builder, block)

        if (enableSsl) {
            withUrl(URL("https://127.0.0.1:$sslPort$path"), sslPort, builder, block)
        }

        if (enableHttp2 && enableSsl) {
            withHttp2(URL("https://127.0.0.1:$sslPort$path"), sslPort, builder, block)
        }
    }

    protected fun socket(block: Socket.() -> Unit) {
        Socket("localhost", port).use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = socketReadTimeout

            block(socket)
        }
    }

    private fun withUrl(url: URL, port: Int, builder: HttpRequestBuilder.() -> Unit, block: suspend HttpResponse.(Int) -> Unit) {
        runBlocking {
            withTimeout(timeout.seconds, TimeUnit.SECONDS) {
                val client = HttpClient(ApacheBackend.config {
                    sslContext = Companion.sslContext
                })

                client.call(url, builder).use { response ->
                    block(response, port)
                }

                client.close()
            }
        }
    }

    private fun withHttp2(url: URL, port: Int, builder: HttpRequestBuilder.() -> Unit, block: suspend HttpResponse.(Int) -> Unit) {
        runBlocking {
            withTimeout(timeout.seconds, TimeUnit.SECONDS) {
                HttpClient(JettyHttp2Backend).use { httpClient ->
                    httpClient.call(url, builder).use { response ->
                        block(response, port)
                    }
                }
            }
        }
    }

    class PublishedTimeout(val seconds: Long) : Timeout(seconds, TimeUnit.SECONDS)

    companion object {
        val keyStoreFile = File("build/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslContext: SSLContext

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificate(keyStoreFile)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
        }

        private suspend fun CoroutineScope.waitForPort(port: Int) {
            do {
                delay(50)
                try {
                    Socket("localhost", port).close()
                    break
                } catch (expected: IOException) {
                }
            } while (isActive)
        }
    }
}