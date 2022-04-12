/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.tomcat

import io.ktor.http.*
import io.ktor.server.application.*
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
import org.slf4j.*
import java.nio.file.*
import java.util.concurrent.*
import javax.servlet.*
import kotlin.coroutines.*

/**
 * Tomcat application engine that runs it in embedded mode
 */
public class TomcatApplicationEngine(
    environment: ApplicationEngineEnvironment,
    configure: Configuration.() -> Unit
) : BaseApplicationEngine(environment) {
    /**
     * Tomcat engine specific configuration builder
     */
    public class Configuration : BaseApplicationEngine.Configuration() {
        /**
         * Property to provide a lambda that will be called
         * during Tomcat server initialization with the server instance as argument.
         */
        public var configureTomcat: Tomcat.() -> Unit = {}
    }

    private val configuration = Configuration().apply(configure)

    private val tempDirectory by lazy { Files.createTempDirectory("ktor-server-tomcat-") }

    private var cancellationDeferred: CompletableJob? = null

    private val ktorServlet = object : KtorServlet() {
        override val managedByEngineHeaders: Set<String>
            get() = setOf(HttpHeaders.TransferEncoding)
        override val enginePipeline: EnginePipeline
            get() = this@TomcatApplicationEngine.pipeline
        override val application: Application
            get() = this@TomcatApplicationEngine.application
        override val upgrade: ServletUpgrade
            get() = DefaultServletUpgrade
        override val logger: Logger
            get() = this@TomcatApplicationEngine.environment.log
        override val coroutineContext: CoroutineContext
            get() = super.coroutineContext + environment.parentCoroutineContext
    }

    private val server = Tomcat().apply {
        configuration.configureTomcat(this)
        service.apply {
            findConnectors().forEach { existing ->
                removeConnector(existing)
            }

            environment.connectors.forEach { connector ->
                addConnector(
                    Connector().apply {
                        port = connector.port

                        if (connector is EngineSSLConnectorConfig) {
                            secure = true
                            scheme = "https"

                            configureAuthentication(connector)
                            configureVerification(connector)

                            setProperty("sslProtocol", "TLS")
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
        environment.start()
        server.start()

        val connectors = server.service.findConnectors().zip(environment.connectors)
            .map { it.second.withPort(it.first.localPort) }
        resolvedConnectors.complete(connectors)

        cancellationDeferred = stopServerOnCancellation()
        if (wait) {
            server.server.await()
            stop(1, 5, TimeUnit.SECONDS)
        }
        return this
    }

    override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
        if (!stopped.compareAndSet(expect = false, update = true)) return

        cancellationDeferred?.complete()
        environment.monitor.raise(ApplicationStopPreparing, environment)
        server.stop()
        environment.stop()
        server.destroy()
        tempDirectory.toFile().deleteRecursively()
    }

    public companion object {
        //TODO: is it correct?
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

private fun Connector.configureAuthentication(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.authentication?.let { config ->
        config.keyStoreProvider?.let { ksp ->
            check(ksp is KeyStoreProvider.File) { "Tomcat requires KeyStoreProvider.File for EngineSSLConnectorConfig" }
            setProperty("keyAlias", config.keyAlias)
            setProperty("keystoreFile", ksp.path.absolutePath)
            setProperty("keystoreType", ksp.type)
            ksp.passwordProvider?.let {
                setProperty("keystorePass", String(it()))
            }

            setProperty("keyPass", String(config.privateKeyPassword()))
            return
        }
    }

    //old configuration

    if (connectorConfig.keyStorePath == null) {
        throw IllegalArgumentException(
            "Tomcat requires keyStorePath. Make sure you're setting " +
                "the property in the EngineSSLConnectorConfig class."
        )
    }

    setProperty("keyAlias", connectorConfig.keyAlias)
    setProperty("keystorePass", String(connectorConfig.keyStorePassword()))
    setProperty("keyPass", String(connectorConfig.privateKeyPassword()))
    setProperty("keystoreFile", connectorConfig.keyStorePath!!.absolutePath)

}

private fun Connector.configureVerification(connectorConfig: EngineSSLConnectorConfig) {
    connectorConfig.verification?.let { config ->
        config.trustStoreProvider?.let { ksp ->
            check(ksp is KeyStoreProvider.File) { "Tomcat requires KeyStoreProvider.File for EngineSSLConnectorConfig" }
            setProperty("clientAuth", "true")
            setProperty("truststoreFile", ksp.path.absolutePath)
            setProperty("truststoreType", ksp.type)
            ksp.passwordProvider?.let {
                setProperty("truststorePass", String(it()))
            }
            return
        }
    }

    //old configuration

    if (connectorConfig.trustStore != null && connectorConfig.trustStorePath == null) {
        throw IllegalArgumentException(
            "Tomcat requires trustStorePath for client certificate authentication." +
                "Make sure you're setting the property in the EngineSSLConnectorConfig class."
        )
    }
    if (connectorConfig.trustStorePath != null) {
        setProperty("clientAuth", "true")
        setProperty("truststoreFile", connectorConfig.trustStorePath!!.absolutePath)
    } else {
        setProperty("clientAuth", "false")
    }

}
