// ktlint-disable filename
/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.testing

import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.network.tls.certificates.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.coroutines.*
import org.junit.*
import org.junit.runners.model.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.security.*
import java.security.cert.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.coroutines.*
import kotlin.time.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Suppress("KDocMissingDocumentation")
public actual abstract class EngineTestBase<
    TEngine : ApplicationEngine,
    TConfiguration : ApplicationEngine.Configuration> actual constructor(
    public actual val applicationEngineFactory: ApplicationEngineFactory<TEngine, TConfiguration>,
) : BaseTest(), CoroutineScope {
    private val testJob = Job()

    @OptIn(ExperimentalCoroutinesApi::class)
    public val testDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(32)

    protected val isUnderDebugger: Boolean =
        java.lang.management.ManagementFactory.getRuntimeMXBean().inputArguments.orEmpty()
            .any { "-agentlib:jdwp" in it }

    protected actual var port: Int = findFreePort()
    protected actual var sslPort: Int = findFreePort()
    protected actual var server: TEngine? = null
    protected var callGroupSize: Int = -1
        private set
    protected actual var enableHttp2: Boolean = System.getProperty("enable.http2") == "true"
    protected actual var enableSsl: Boolean = System.getProperty("enable.ssl") != "false"
    protected actual var enableCertVerify: Boolean = System.getProperty("enable.cert.verify") == "true"

    private val allConnections = CopyOnWriteArrayList<HttpURLConnection>()

    public val testLog: Logger = LoggerFactory.getLogger("io.ktor.test.EngineTestBase")

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected actual annotation class Http2Only actual constructor()

    @Target(AnnotationTarget.FUNCTION)
    @Retention
    protected annotation class NoHttp2

    actual override val coroutineContext: CoroutineContext
        get() = testJob + testDispatcher

    override val timeout: Duration = if (isUnderDebugger) {
        1_000_000.milliseconds
    } else {
        System.getProperty("host.test.timeout.seconds")?.toLong()?.seconds ?: 4.minutes
    }

    @Before
    public fun setUpBase() {
        val method = this.javaClass.getMethod(testName.methodName)
            ?: throw AssertionError("Method ${testName.methodName} not found")

        if (method.isAnnotationPresent(Http2Only::class.java)) {
            Assume.assumeTrue("http2 is not enabled", enableHttp2)
        }
        if (method.isAnnotationPresent(NoHttp2::class.java)) {
            enableHttp2 = false
        }

        testLog.trace("Starting server on port $port (SSL $sslPort)")
    }

    @After
    public fun tearDownBase() {
        try {
            allConnections.forEach { it.disconnect() }
            testLog.trace("Disposing server on port $port (SSL $sslPort)")
            (server as? ApplicationEngine)?.stop(0, 200, TimeUnit.MILLISECONDS)
        } finally {
            testJob.cancel()
            FreePorts.recycle(port)
            FreePorts.recycle(sslPort)
        }
    }

    protected open fun createServer(
        log: Logger? = null,
        parent: CoroutineContext = EmptyCoroutineContext,
        module: Application.() -> Unit
    ): TEngine {
        val _port = this.port
        val environment = applicationEngineEnvironment {
            this.parentCoroutineContext = parent
            val delegate = LoggerFactory.getLogger("io.ktor.test")
            this.log = log ?: object : Logger by delegate {
                override fun error(msg: String?, t: Throwable?) {
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

            connector { port = _port }
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

            module(module)
        }

        return embeddedServer(applicationEngineFactory, environment) {
            configure(this)
            this@EngineTestBase.callGroupSize = callGroupSize
        }
    }

    protected open fun configure(configuration: TConfiguration) {
        // Empty, intended to be override in derived types when necessary
    }

    protected actual open fun plugins(application: Application, routingConfigurer: Routing.() -> Unit) {
        application.install(CallLogging)
        application.install(Routing, routingConfigurer)
    }

    protected actual fun createAndStartServer(
        log: Logger?,
        parent: CoroutineContext,
        routingConfigurer: Routing.() -> Unit
    ): TEngine {
        var lastFailures = emptyList<Throwable>()
        for (attempt in 1..5) {
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
                    sslPort = findFreePort()
                    server.stop(1L, 1L, TimeUnit.SECONDS)
                    lastFailures = failures
                }

                else -> {
                    server.stop(1L, 1L, TimeUnit.SECONDS)
                    throw MultipleFailureException(failures)
                }
            }
        }

        throw MultipleFailureException(lastFailures)
    }

    @OptIn(DelicateCoroutinesApi::class)
    protected fun startServer(server: TEngine): List<Throwable> {
        this.server = server

        // we start it on the global scope because we don't want it to fail the whole test
        // as far as we have retry loop on call side
        val starting = GlobalScope.async(testDispatcher) {
            server.start(wait = false)

            withTimeout(minOf(10.seconds, timeout)) {
                server.environment.connectors.forEach { connector ->
                    waitForPort(connector.port)
                }
            }
        }

        return try {
            runBlocking {
                starting.join()
                @OptIn(ExperimentalCoroutinesApi::class)
                starting.getCompletionExceptionOrNull()?.let { listOf(it) } ?: emptyList()
            }
        } catch (t: Throwable) { // InterruptedException?
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

    protected fun findFreePort(): Int = FreePorts.select()

    protected actual fun withUrl(
        path: String,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) {
        withUrl("http://127.0.0.1:$port$path", port, builder, block)

        if (enableSsl) {
            withUrl("https://127.0.0.1:$sslPort$path", sslPort, builder, block)
        }

        if (enableHttp2 && enableSsl) {
            withHttp2("https://127.0.0.1:$sslPort$path", sslPort, builder, block)
        }
    }

    protected inline fun socket(block: Socket.() -> Unit) {
        Socket().use { socket ->
            socket.tcpNoDelay = true
            socket.soTimeout = timeout.inWholeMilliseconds.toInt()
            socket.connect(InetSocketAddress("localhost", port))

            block(socket)
        }
    }

    private fun withUrl(
        urlString: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ) = runBlocking {
        HttpClient(CIO) {
            engine {
                https.trustManager = trustManager
                requestTimeout = 0
            }
            followRedirects = false
            expectSuccess = false
        }.use { client ->
            client.prepareRequest {
                url.takeFrom(urlString)
                builder()
            }.execute { response ->
                block(response, port)
            }
        }
    }

    private fun withHttp2(
        url: String,
        port: Int,
        builder: suspend HttpRequestBuilder.() -> Unit,
        block: suspend HttpResponse.(Int) -> Unit
    ): Unit = runBlocking {
        HttpClient(Apache) {
            followRedirects = false
            expectSuccess = false
            engine {
                pipelining = true
                sslContext = SSLContext.getInstance("SSL").apply {
                    init(null, trustAllCertificates, SecureRandom())
                }
            }
        }.use { client ->
            client.prepareRequest(url) {
                builder()
            }.execute { response ->
                block(response, port)
            }
        }
    }

    public companion object {
        public val keyStoreFile: File = File("build/temp.jks")
        public lateinit var keyStore: KeyStore
        public lateinit var sslContext: SSLContext
        public lateinit var trustManager: X509TrustManager

        @BeforeClass
        @JvmStatic
        public fun setupAll() {
            keyStore = generateCertificate(keyStoreFile, algorithm = "SHA256withECDSA", keySizeInBits = 256)
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, tmf.trustManagers, null)
            trustManager = tmf.trustManagers.first { it is X509TrustManager } as X509TrustManager
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
    }

    private val trustAllCertificates = arrayOf<X509TrustManager>(
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) {}
        }
    )
}
