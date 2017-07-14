package org.jetbrains.ktor.testing

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.client.http2.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import org.junit.rules.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.security.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.concurrent.*
import kotlin.test.*


abstract class HostTestBase<THost : ApplicationHost>(val applicationHostFactory: ApplicationHostFactory<THost>) {
    protected val isUnderDebugger = java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.orEmpty().any { "-agentlib:jdwp" in it }
    protected val port = findFreePort()
    protected val sslPort = findFreePort()
    protected var server: THost? = null
    protected var enableHttp2: Boolean = System.getProperty("enable.http2") == "true"
    private val allConnections = CopyOnWriteArrayList<HttpURLConnection>()

    val testLog: Logger = LoggerFactory.getLogger("HostTestBase")

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class Http2Only

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class NoHttp2

    @get:Rule
    val test = TestName()

    @get:Rule
    val timeout = PublishedTimeout(if (isUnderDebugger) 1000000L else 120L)

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
    }

    @After
    fun tearDownBase() {
        allConnections.forEach { it.disconnect() }
        testLog.trace("Disposing server on port $port (SSL $sslPort)")
        (server as? ApplicationHost)?.stop(1000, 5000, TimeUnit.MILLISECONDS)
    }

    protected open fun createServer(log: Logger?, module: Application.() -> Unit): THost {
        val _port = this.port
        val environment = applicationHostEnvironment {
            this.log = log ?: LoggerFactory.getLogger("ktor.test")
            connector { port = _port }
            sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                this.port = sslPort
                this.keyStorePath = keyStoreFile.absoluteFile
            }

            module(module)
        }
        return embeddedServer(applicationHostFactory, environment)
    }

    protected fun createAndStartServer(log: Logger? = null, block: Routing.() -> Unit): THost {
        val server = createServer(log) {
            install(CallLogging)
            install(Routing, block)
        }
        startServer(server)

        return server
    }

    protected fun startServer(server: THost) {
        this.server = server
        val l = CountDownLatch(1)
        thread {
            l.countDown()
            (server as? ApplicationHost)?.start()
        }
        l.await()

        server.environment.connectors.forEach { connector ->
            waitForPort(connector.port)
        }
    }

    protected fun findFreePort() = ServerSocket(0).use { it.localPort }
    protected fun withUrl(path: String, builder: RequestBuilder.() -> Unit = {}, block: suspend HttpResponse.(Int) -> Unit) {
        withUrl(URL("http://127.0.0.1:$port$path"), port, builder, block)
        withUrl(URL("https://127.0.0.1:$sslPort$path"), sslPort, builder, block)

        if (enableHttp2) {
            withHttp2(URL("https://127.0.0.1:$sslPort$path"), sslPort, builder, block)
        }
    }

    private fun withUrl(url: URL, port: Int, builder: RequestBuilder.() -> Unit, block: suspend HttpResponse.(Int) -> Unit) {
        runBlocking {
            withTimeout(timeout.seconds, TimeUnit.SECONDS) {
                DefaultHttpClient.request(url, {
                    this.sslSocketFactory = Companion.sslSocketFactory
                    builder()
                }).use { response ->
                    block(response, port)
                }
            }
        }
    }

    private fun withHttp2(url: URL, port: Int, builder: RequestBuilder.() -> Unit, block: suspend HttpResponse.(Int) -> Unit) {
        runBlocking {
            withTimeout(timeout.seconds, TimeUnit.SECONDS) {
                Http2Client.request(url, builder).use { response ->
                    block(response, port)
                }
            }
        }
    }

    class PublishedTimeout(val seconds: Long) : Timeout(seconds, TimeUnit.SECONDS)

    companion object {
        val keyStoreFile = File("target/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslSocketFactory: SSLSocketFactory

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificate(keyStoreFile)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, null)
            sslSocketFactory = ctx.socketFactory
        }

        private fun waitForPort(port: Int) {
            do {
                Thread.sleep(50)
                try {
                    Socket("localhost", port).close()
                    break
                } catch (expected: IOException) {
                }
            } while (true)
        }
    }
}