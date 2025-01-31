/*
 * Copyright 2014-2023 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat.jakarta

import io.ktor.events.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.servlet.jakarta.*
import jakarta.servlet.MultipartConfigElement
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CompletableJob
import org.apache.catalina.connector.Connector
import org.apache.catalina.startup.Tomcat
import org.apache.coyote.http2.Http2Protocol
import org.apache.tomcat.jni.Library
import org.apache.tomcat.jni.SSL
import org.apache.tomcat.util.net.SSLHostConfig
import org.apache.tomcat.util.net.SSLHostConfigCertificate
import org.apache.tomcat.util.net.SSLImplementation
import org.apache.tomcat.util.net.jsse.JSSEImplementation
import org.apache.tomcat.util.net.openssl.OpenSSLImplementation
import org.slf4j.Logger
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext

/**
 * Tomcat application engine that runs it in embedded mode
 *
 * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.tomcat.jakarta.TomcatApplicationEngine)
 */
public class TomcatApplicationEngine(
    environment: ApplicationEnvironment,
    monitor: Events,
    developmentMode: Boolean,
    public val configuration: Configuration,
    private val applicationProvider: () -> Application
) : BaseApplicationEngine(environment, monitor, developmentMode) {
    /**
     * Tomcat engine specific configuration builder
     *
     * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.tomcat.jakarta.TomcatApplicationEngine.Configuration)
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property to provide a lambda that will be called
         * during Tomcat server initialization with the server instance as argument.
         *
         * [Report a problem](https://ktor.io/feedback/?fqname=io.ktor.server.tomcat.jakarta.TomcatApplicationEngine.Configuration.configureTomcat)
         */
        public var configureTomcat: Tomcat.() -> Unit = {}
    }

    private val tempDirectory by lazy { Files.createTempDirectory("ktor-server-tomcat-jakarta-") }

    private var cancellationJob: CompletableJob? = null

    private val ktorServlet = object : KtorServlet() {
        override val managedByEngineHeaders: Set<String>
            get() = setOf(HttpHeaders.TransferEncoding)
        override val enginePipeline: EnginePipeline
            get() = this@TomcatApplicationEngine.pipeline
        override val application: Application
            get() = this@TomcatApplicationEngine.applicationProvider()
        override val upgrade: ServletUpgrade
            get() = DefaultServletUpgrade
        override val logger: Logger
            get() = this@TomcatApplicationEngine.environment.log
        override val coroutineContext: CoroutineContext
            get() = super.coroutineContext + application.parentCoroutineContext
    }

    private val server = Tomcat().apply {
        configuration.configureTomcat(this)
        service.apply {
            findConnectors().forEach { existing ->
                removeConnector(existing)
            }

            configuration.connectors.forEach { ktorConnector ->
                addConnector(
                    Connector().apply {
                        port = ktorConnector.port

                        if (ktorConnector is EngineSSLConnectorConfig) {
                            secure = true
                            scheme = "https"

                            if (ktorConnector.keyStorePath == null) {
                                throw IllegalArgumentException(
                                    "Tomcat requires keyStorePath. Make sure you're setting " +
                                        "the property in the EngineSSLConnectorConfig class."
                                )
                            }

                            if (ktorConnector.trustStore != null && ktorConnector.trustStorePath == null) {
                                throw IllegalArgumentException(
                                    "Tomcat requires trustStorePath for client certificate authentication." +
                                        "Make sure you're setting the property in the EngineSSLConnectorConfig class."
                                )
                            }

                            addSslHostConfig(
                                SSLHostConfig().apply {
                                    if (ktorConnector.trustStorePath != null) {
                                        setProperty("clientAuth", "true")
                                        truststoreFile = ktorConnector.trustStorePath!!.absolutePath
                                    } else {
                                        setProperty("clientAuth", "false")
                                    }

                                    sslProtocol = "TLS"
                                    setProperty("SSLEnabled", "true")
                                    addCertificate(
                                        SSLHostConfigCertificate(
                                            this,
                                            SSLHostConfigCertificate.Type.UNDEFINED
                                        ).apply {
                                            certificateKeyAlias = ktorConnector.keyAlias
                                            certificateKeystorePassword = String(ktorConnector.keyStorePassword())
                                            certificateKeyPassword = String(ktorConnector.privateKeyPassword())
                                            certificateKeystoreFile = ktorConnector.keyStorePath!!.absolutePath
                                        }
                                    )

                                    ktorConnector.enabledProtocols?.let {
                                        enabledProtocols = it.toTypedArray()
                                    }
                                }
                            )

                            setProperty("SSLEnabled", "true")

                            val sslImpl = chooseSSLImplementation()

                            setProperty("sslImplementationName", sslImpl.name)

                            if (sslImpl.simpleName == "OpenSSLImplementation") {
                                addUpgradeProtocol(Http2Protocol())
                            }
                        } else {
                            scheme = "http"
                        }
                    }
                )
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
        server.start()

        val connectors = server.service.findConnectors().zip(configuration.connectors)
            .map { it.second.withPort(it.first.localPort) }
        resolvedConnectorsDeferred.complete(connectors)
        monitor.raiseCatching(ServerReady, environment, environment.log)

        cancellationJob = stopServerOnCancellation(
            applicationProvider(),
            configuration.shutdownGracePeriod,
            configuration.shutdownTimeout
        )
        if (wait) {
            server.server.await()
            stop(configuration.shutdownGracePeriod, configuration.shutdownTimeout)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        if (!stopped.compareAndSet(expect = false, update = true)) return

        cancellationJob?.complete()
        monitor.raise(ApplicationStopPreparing, environment)
        server.stop()
        server.destroy()
        tempDirectory.toFile().deleteRecursively()
    }

    public companion object {
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
