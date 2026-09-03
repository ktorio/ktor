/*
 * Copyright 2014-2025 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.test.base

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.test.*
import io.ktor.test.junit.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.net.BindException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

actual abstract class EngineTestBase<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration
    > actual constructor(
    actual val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : BaseTest(), CoroutineScope {
    private val testJob = Job()

    protected val testDispatcher = Dispatchers.IO.limitedParallelism(32)

    protected val isUnderDebugger: Boolean =
        java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.orEmpty()
            .any { "-agentlib:jdwp" in it }

    protected actual var port: Int = findFreePort()

    // Allocated UDP-aware because an HTTP/3 server binds UDP on its TCP SSL connector's port.
    protected actual var sslPort: Int = findFreeUdpPort()
    protected actual var server: EmbeddedServer<TEngine, TConfiguration>? = null
    protected var callGroupSize: Int = -1
        private set
    protected actual var enableHttp2: Boolean = System.getProperty("enable.http2") == "true"
    protected actual var enableHttp3: Boolean = System.getProperty("enable.http3") == "true"
    protected actual var enableSsl: Boolean = System.getProperty("enable.ssl") != "false"
    protected actual var enableCertVerify: Boolean = System.getProperty("enable.cert.verify") == "true"

    /**
     * Restricts [withUrl] to the HTTP/3 leg, so a dedicated HTTP/3 test run does not re-run every
     * shared suite case over HTTP/1.1 and HTTP/2 as well.
     */
    protected var http3Only: Boolean = System.getProperty("enable.http3.only") == "true"

    private val allConnections = CopyOnWriteArrayList<HttpURLConnection>()

    val testLog: Logger = LoggerFactory.getLogger("io.ktor.test.EngineTestBase")

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http2Only actual constructor()

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http1Only actual constructor()

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http3Only actual constructor()

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http3Excluded actual constructor()

    actual override val coroutineContext: CoroutineContext
        get() = testJob + testDispatcher

    override val timeout: Duration = if (isUnderDebugger) {
        1_000_000.milliseconds
    } else {
        System.getProperty("host.test.timeout.seconds")?.toLong()?.seconds ?: DEFAULT_TEST_TIMEOUT
    }

    override fun beforeTest() {
        super.beforeTest()

        val method = testMethod.orElseThrow { AssertionError("Method $testName not found") }

        if (method.isAnnotationPresent(Http2Only::class.java)) {
            assumeTrue(enableHttp2, "http2 is not enabled")
        }
        if (method.isAnnotationPresent(Http3Only::class.java)) {
            assumeTrue(enableHttp3, "http3 is not enabled")
        }
        if (method.isAnnotationPresent(Http1Only::class.java)) {
            // An http1-only test has no leg left to run when the run is restricted to HTTP/3.
            assumeTrue(!http3Only, "test is http1-only")
            enableHttp2 = false
            enableHttp3 = false
        }
        if (method.isAnnotationPresent(Http3Excluded::class.java)) {
            // In a dedicated HTTP/3 run there is no other leg left to exercise, so skip outright.
            assumeTrue(!http3Only, "test is excluded from the http3 leg")
            enableHttp3 = false
        }

        testLog.trace { "Starting server on port $port (SSL $sslPort)" }
    }

    override fun afterTest() {
        try {
            allConnections.forEach { it.disconnect() }
            testLog.trace { "Disposing server on port $port (SSL $sslPort)" }
            server?.stop(0, 500, TimeUnit.MILLISECONDS)
        } finally {
            resetHttp3Client()
            testJob.cancel()
            FreePorts.recycle(port)
            FreePorts.recycle(sslPort)
            super.afterTest()
        }
    }

    protected open fun createServer(
        log: Logger? = null,
        parent: CoroutineContext = EmptyCoroutineContext,
        module: Application.() -> Unit
    ): EmbeddedServer<TEngine, TConfiguration> {
        val savedPort = this.port
        val environment = applicationEnvironment {
            val delegate = LoggerFactory.getLogger("io.ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(msg: String?, t: Throwable?) {
                    if (t is ExpectedTestException) return
                    t?.let {
                        collectUnhandledException(it)
                        println("Critical test exception: $it")
                        it.printStackTrace()
                        println("From origin:")
                        Exception().printStackTrace()
                    }
                    delegate.error(msg, t)
                }
            }
        }
        val properties = serverConfig(environment) {
            this.parentCoroutineContext = parent
            module(module)
        }

        return embeddedServer(applicationEngineFactory, properties) {
            shutdownGracePeriod = 1000
            shutdownTimeout = 1000

            connector { port = savedPort }
            if (enableSsl) {
                sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                    this.port = sslPort
                    this.keyStorePath = keyStoreFile.absoluteFile
                    if (enableCertVerify) {
                        this.trustStore = keyStore
                        this.trustStorePath = keyStoreFile.absoluteFile
                    }
                }
            }
            configure(this)
            this@EngineTestBase.callGroupSize = callGroupSize
        }
    }

    protected open fun configure(configuration: TConfiguration) {
        // Empty, intended to be override in derived types when necessary
    }

    protected actual open fun plugins(application: Application, routingConfig: Route.() -> Unit) {
        application.install(CallLogging)
        application.install(RoutingRoot, routingConfig)
    }

    protected actual suspend fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Route.() -> Unit
    ): EmbeddedServer<TEngine, TConfiguration> {
        var lastFailures = emptyList<Throwable>()
        repeat(5) {
            val server = createServer(log, parent) {
                plugins(this, routingConfigurer)
            }

            val failures = startServer(server)
            when {
                failures.isEmpty() -> return server

                failures.any { it.hasBindException() || it is TimeoutCancellationException } -> {
                    FreePorts.recycle(port)
                    FreePorts.recycle(sslPort)

                    port = findFreePort()
                    sslPort = findFreeUdpPort()
                    server.stop()
                    lastFailures = failures
                }

                else -> {
                    server.stop()
                    throw MultipleFailureException(failures)
                }
            }
        }

        throw MultipleFailureException(lastFailures)
    }

    @OptIn(DelicateCoroutinesApi::class)
    protected actual suspend fun startServer(server: EmbeddedServer<TEngine, TConfiguration>): List<Throwable> {
        resetHttp3Client()
        this.server = server

        // we start it on the global scope because we don't want it to fail the whole test
        // as far as we have retry loop on the call side
        val starting = GlobalScope.async(testDispatcher) {
            server.start(wait = false)

            withTimeout(minOf(10.seconds, timeout)) {
                server.engineConfig.connectors.forEach { connector ->
                    waitForPort(connector.port)
                }
            }
        }

        return try {
            starting.join()
            @OptIn(ExperimentalCoroutinesApi::class)
            starting.getCompletionExceptionOrNull()?.let { listOf(it) } ?: emptyList()
        } catch (t: Throwable) {
            starting.cancel()
            listOf(t)
        }
    }

    private fun Throwable.hasBindException(): Boolean {
        if (this is BindException) return true
        val cause = cause
        if (cause is BindException) return true
        if (cause == null) return false

        val all = HashSet<Throwable>()
        all.add(this)

        var current: Throwable = cause
        do {
            if (!all.add(current)) break
            current = current.cause ?: break
            if (current is BindException) return true
        } while (true)

        return false
    }

    /**
     * Drops the HTTP/3 client and with it its QUIC connection.
     *
     * QUIC learns that a peer is gone only by idle timeout, so a connection to a server that has
     * just been stopped still reports itself active. Several suite tests replace the server within
     * one test, on the port the previous one just released, so the connection has to be dropped
     * whenever a server starts or a test ends — otherwise requests go to a peer that is no longer
     * listening and simply hang.
     */
    private fun resetHttp3Client() {
        http3Client?.let {
            it.close()
            http3Client = null
        }
    }

    protected fun findFreePort(): Int = FreePorts.select()

    /**
     * Returns a port that is free for both TCP and UDP, as an HTTP/3 connector needs: it binds UDP
     * on the same port its TCP SSL connector listens on.
     */
    protected fun findFreeUdpPort(): Int = FreePorts.selectTcpAndUdp()

    protected actual suspend fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        check(withUrlLegCount > 0) {
            "No protocol leg is enabled, so this call would assert nothing. In an http3-only run " +
                "this means the test disabled SSL, which HTTP/3 requires: mark it @Http3Excluded."
        }

        if (!http3Only) {
            withHttp1("http://127.0.0.1:$port$path", port, builder, block)

            if (enableSsl) {
                withHttp1("https://127.0.0.1:$sslPort$path", sslPort, builder, block)
            }

            if (enableHttp2 && enableSsl) {
                withHttp2("https://127.0.0.1:$sslPort$path", sslPort, builder, block)
            }
        }

        if (enableHttp3 && enableSsl) {
            withHttp3("https://127.0.0.1:$sslPort$path", sslPort, builder, block)
        }
    }

    /**
     * How many protocol legs one [withUrl] call runs.
     *
     * Tests that count server-side invocations must scale by this rather than deriving it from the
     * `enable*` flags themselves, so they stay correct as legs are added or suppressed.
     */
    protected val withUrlLegCount: Int
        get() {
            var legs = 0
            if (!http3Only) {
                legs++
                if (enableSsl) legs++
                if (enableHttp2 && enableSsl) legs++
            }
            if (enableHttp3 && enableSsl) legs++
            return legs
        }

    protected inline fun socket(block: Socket.() -> Unit) {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = timeout.inWholeMilliseconds.toInt()
            socket.connect(InetSocketAddress("localhost", port))

            block(socket)
        }
    }

    protected suspend fun withHttp1(
        urlString: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        client.prepareRequest {
            url.takeFrom(urlString)
            builder()
        }.execute { response ->
            block(response, port)
        }
    }

    protected suspend fun withHttp2(
        url: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        val client = http2Client ?: createApacheClient().also {
            http2Client = it
        }
        client.prepareRequest(url) {
            builder()
        }.execute { response ->
            block(response, port)
        }
    }

    /**
     * Runs the request over HTTP/3.
     *
     * Ktor has no HTTP/3 client engine, so this leg is driven by [Http3TestClientEngine]. The
     * client is shared like [http2Client]; its engine holds one live QUIC connection and reopens it
     * whenever the authority changes, which it does for every test that starts a fresh server.
     */
    protected suspend fun withHttp3(
        url: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        val client = http3Client ?: createHttp3Client().also {
            http3Client = it
        }
        client.prepareRequest(url) {
            builder()
        }.execute { response ->
            block(response, port)
        }
    }

    companion object {
        val keyStoreFile: File = File("build/temp.jks")
        val keyStore: KeyStore by lazy { generateCertificate(keyStoreFile) }
        lateinit var client: HttpClient
        var http2Client: HttpClient? = null
        var http3Client: HttpClient? = null

        fun createTrustManager(): X509TrustManager {
            val sslContext = SSLContext.getInstance("TLS")
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext.init(null, tmf.trustManagers, null)
            return tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
        }

        fun createCIOClient(): HttpClient {
            return HttpClient(CIO) {
                engine {
                    https.trustManager = createTrustManager()
                    https.serverName = "localhost"
                    requestTimeout = 0
                }
                followRedirects = false
                expectSuccess = false
            }
        }

        fun createApacheClient(): HttpClient {
            return HttpClient(Apache5) {
                followRedirects = false
                expectSuccess = false
                engine {
                    customizeClient {
                        disableAutomaticRetries()
                    }
                    pipelining = true
                    sslContext = SSLContext.getInstance("TLS").apply {
                        init(null, trustAllCertificates, SecureRandom())
                    }
                }
            }
        }

        /**
         * The engine reconnects when the authority changes, so one client can serve every test in
         * the run even though each test starts its server on a fresh port.
         */
        fun createHttp3Client(): HttpClient {
            return HttpClient(Http3TestEngine) {
                followRedirects = false
                expectSuccess = false
            }
        }

        @BeforeAll
        @JvmStatic
        fun setupAll() {
            client = createCIOClient()
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            client.close()
            http2Client?.let {
                it.close()
                http2Client = null
            }
            http3Client?.let {
                it.close()
                http3Client = null
            }
        }

        @Suppress("BlockingMethodInNonBlockingContext")
        private suspend fun waitForPort(port: Int) {
            do {
                delay(50)
                try {
                    Socket("localhost", port).close()
                    break
                } catch (_: IOException) {
                }
            } while (true)
        }

        private val trustAllCertificates = arrayOf<X509TrustManager>(
            @Suppress("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

                @Suppress("TrustAllX509TrustManager")
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {
                }

                @Suppress("TrustAllX509TrustManager")
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {
                }
            }
        )
    }
}
