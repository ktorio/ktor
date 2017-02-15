package org.jetbrains.ktor.testing

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.client.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.logging.*
import org.jetbrains.ktor.routing.*
import org.jetbrains.ktor.util.*
import org.junit.*
import org.slf4j.*
import java.io.*
import java.net.*
import java.security.*
import java.util.concurrent.*
import javax.net.ssl.*
import kotlin.concurrent.*

abstract class HostTestBase<H : ApplicationHost> {
    protected val port = findFreePort()
    protected val sslPort = findFreePort()
    protected var server: H? = null

    val testLog: Logger = LoggerFactory.getLogger("HostTestBase")

    @Before
    fun setUpBase() {
        testLog.trace("Starting server on port $port (SSL $sslPort)")
    }

    @After
    fun tearDownBase() {
        testLog.trace("Disposing server on port $port (SSL $sslPort)")
        (server as? ApplicationHostStartable)?.stop(100, 5000, TimeUnit.MILLISECONDS)
    }

    protected abstract fun createServer(envInit: ApplicationEnvironmentBuilder.() -> Unit, routing: Routing.() -> Unit): H

    protected fun createAndStartServer(envInit: ApplicationEnvironmentBuilder.() -> Unit = {}, block: Routing.() -> Unit): H {
        val server = createServer(envInit, {
            application.install(CallLogging)
            block()
        })
        startServer(server)

        return server
    }

    protected fun startServer(server: H) {
        this.server = server
        val l = CountDownLatch(1)
        thread {
            l.countDown()
            (server as? ApplicationHostStartable)?.start()
        }
        l.await()

        server.hostConfig.connectors.forEach { connector ->
            waitForPort(connector.port)
        }
    }

    protected fun findFreePort() = ServerSocket(0).use { it.localPort }
    protected fun withUrl(path: String, block: HttpURLConnection.(Int) -> Unit) {
        withUrl(URL("http://127.0.0.1:$port$path"), port, block)
        withUrl(URL("https://127.0.0.1:$sslPort$path"), sslPort, block)
    }

    protected fun withUrlHttp2(path: String, block: Unit.(Int) -> Unit) {
        withHttp2(URL("http://127.0.0.1:$port$path"), port, block)
//        withHttp2(URL("https://127.0.0.1:$sslPort$path"), sslPort, block)
    }

    private fun withUrl(url: URL, port: Int, block: HttpURLConnection.(Int) -> Unit) {
        val connection = url.openConnection() as HttpURLConnection
        if (connection is HttpsURLConnection) {
            connection.sslSocketFactory = sslSocketFactory
        }
        connection.connectTimeout = 10000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = false
        connection.block(port)
    }

    private fun withHttp2(url: URL, port: Int, block: Unit.(Int) -> Unit) {
//        val transport = HTTP2Client()
//        val client = HttpClient(HttpClientTransportOverHTTP2(transport), null)
//
//        transport.start()
//        client.start()
//
//        try {
//            client.GET(url.toURI()).apply {
//                assertEquals("HTTP/2.0", version.asString())
//                block(port)
//            }
//        } finally {
//            client.stop()
//            transport.stop()
//        }
    }

    companion object {
        val keyStoreFile = File("target/temp.jks")
        lateinit var keyStore: KeyStore
        lateinit var sslSocketFactory: SSLSocketFactory
        lateinit var hostConfig: (Int, Int) -> ApplicationHostConfig

        @BeforeClass
        @JvmStatic
        fun setupAll() {
            keyStore = generateCertificate(keyStoreFile)

            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            tmf.init(keyStore)
            val ctx = SSLContext.getInstance("TLS")
            ctx.init(null, tmf.trustManagers, null)

            sslSocketFactory = ctx.socketFactory
            hostConfig = { port, sslPort ->
                applicationHostConfig {
                    connector {
                        this.port = port
                    }
                    sslConnector(keyStore, "mykey", { "changeit".toCharArray() }, { "changeit".toCharArray() }) {
                        this.port = sslPort
                        this.keyStorePath = keyStoreFile.absoluteFile
                    }
                }
            }
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