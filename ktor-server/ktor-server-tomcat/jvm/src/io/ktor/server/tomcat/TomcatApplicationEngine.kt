package io.ktor.server.tomcat

import io.ktor.application.*
import io.ktor.server.engine.*
import io.ktor.server.servlet.*
import kotlinx.atomicfu.*
import kotlinx.coroutines.*
import org.apache.catalina.connector.*
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.http2.*
import org.apache.tomcat.jni.*
import org.apache.tomcat.util.net.*
import org.apache.tomcat.util.net.jsse.*
import org.apache.tomcat.util.net.openssl.*
import java.nio.file.*
import java.util.concurrent.*
import javax.servlet.*

/**
 * Tomcat application engine that runs it in embedded mode
 */
class TomcatApplicationEngine(environment: ApplicationEngineEnvironment, configure: Configuration.() -> Unit) :
    BaseApplicationEngine(environment) {
    /**
     * Tomcat engine specific configuration builder
     */
    class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property to provide a lambda that will be called
         * during Tomcat server initialization with the server instance as argument.
         */
        var configureTomcat: Tomcat.() -> Unit = {}
    }

    private val configuration = Configuration().apply(configure)

    private val tempDirectory by lazy { Files.createTempDirectory("ktor-server-tomcat-") }

    private var cancellationDeferred: CompletableJob? = null

    private val ktorServlet = object : KtorServlet() {
        override val enginePipeline: EnginePipeline
            get() = this@TomcatApplicationEngine.pipeline
        override val application: Application
            get() = this@TomcatApplicationEngine.application
        override val upgrade: ServletUpgrade
            get() = DefaultServletUpgrade
    }

    private val server = Tomcat().apply {
        configuration.configureTomcat(this)
        service.apply {
            findConnectors().forEach { existing ->
                removeConnector(existing)
            }

            environment.connectors.forEach { ktorConnector ->
                addConnector(Connector().apply {
                    port = ktorConnector.port

                    if (ktorConnector is EngineSSLConnectorConfig) {
                        secure = true
                        scheme = "https"

                        if (ktorConnector.keyStorePath == null) {
                            throw IllegalArgumentException("Tomcat requires keyStorePath")
                        }

                        setAttribute("keyAlias", ktorConnector.keyAlias)
                        setAttribute("keystorePass", String(ktorConnector.keyStorePassword()))
                        setAttribute("keyPass", String(ktorConnector.privateKeyPassword()))
                        setAttribute("keystoreFile", ktorConnector.keyStorePath!!.absolutePath)
                        setAttribute("clientAuth", false)
                        setAttribute("sslProtocol", "TLS")
                        setAttribute("SSLEnabled", true)

                        val sslImpl = chooseSSLImplementation()

                        setAttribute("sslImplementationName", sslImpl.name)

                        if (sslImpl.simpleName == "OpenSSLImplementation") {
                            addUpgradeProtocol(Http2Protocol())
                        }
                    } else {
                        scheme = "http"
                    }
                })
            }
        }

        if (connector == null) {
            connector = service.findConnectors()?.firstOrNull() ?: Connector().apply { port = 80 }
        }
        setBaseDir(tempDirectory.toString())

        val ctx = addContext("", tempDirectory.toString())

        Tomcat.addServlet(ctx, "ktor-servlet", ktorServlet).apply {
            addMapping("/*")
            isAsyncSupported = true
            multipartConfigElement = MultipartConfigElement("")
        }
    }

    private val stopped = atomic(false)

    override fun start(wait: Boolean): TomcatApplicationEngine {
        environment.start()
        server.start()
        cancellationDeferred = stopServerOnCancellation()
        if (wait) {
            server.server.await()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriod: Long, timeout: Long, timeUnit: TimeUnit) {
        if (stopped.compareAndSet(false, true)) {
            cancellationDeferred?.complete()
            environment.monitor.raise(ApplicationStopPreparing, environment)
            server.stop()
            environment.stop()
            server.destroy()
            tempDirectory.toFile().deleteRecursively()
        }
    }

    companion object {
        private val nativeNames = listOf(
//            "netty-tcnative",
//            "libnetty-tcnative",
//            "netty-tcnative-1",
//            "libnetty-tcnative-1",
//            "tcnative-1",
//            "libtcnative-1",
            "netty-tcnative-windows-x86_64"
        )

        private fun chooseSSLImplementation(): Class<out SSLImplementation> {
            return try {
                val nativeName = nativeNames.firstOrNull { tryLoadLibrary(it) }
                if (nativeName != null) {
                    Library.initialize(nativeName)
                    SSL.initialize(null)
                    SSL.freeSSL(SSL.newSSL(SSL.SSL_PROTOCOL_ALL.toLong(), true))
                    OpenSSLImplementation::class.java
                } else {
                    JSSEImplementation::class.java
                }
            } catch (t: Throwable) {
                JSSEImplementation::class.java
            }
        }

        private fun tryLoadLibrary(libraryName: String): Boolean = try {
            System.loadLibrary(libraryName)
            true
        } catch (t: Throwable) {
            false
        }
    }
}
