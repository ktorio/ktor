package org.jetbrains.ktor.tomcat

import org.apache.catalina.connector.*
import org.apache.catalina.startup.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.features.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.servlet.*
import org.jetbrains.ktor.transform.*
import java.nio.file.*
import javax.servlet.*

class TomcatApplicationHost(override val hostConfig: ApplicationHostConfig,
                            val config: ApplicationEnvironment,
                            val applicationLifecycle: ApplicationLifecycle) : ApplicationHost {


    private val application: Application get() = applicationLifecycle.application
    private val tempDirectory by lazy { Files.createTempDirectory("ktor-tomcat-") }

    constructor(hostConfig: ApplicationHostConfig, config: ApplicationEnvironment)
    : this(hostConfig, config, ApplicationLoader(config, hostConfig.autoreload))

    private val ktorServlet = object : KtorServlet() {
        override val application: Application
            get() = this@TomcatApplicationHost.application
    }

    val server = Tomcat().apply {
        connector = null

        service.apply {
            findConnectors().forEach { existing ->
                removeConnector(existing)
            }

            hostConfig.connectors.forEach { ktorConnector ->
                addConnector(Connector().apply {
                    port = ktorConnector.port

                    if (ktorConnector is HostSSLConnectorConfig) {
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
                    } else {
                        scheme = "http"
                    }
                })
            }
        }

        connector = Connector()
        setBaseDir(tempDirectory.toString())

        val ctx = addContext("", tempDirectory.toString())

        Tomcat.addServlet(ctx, "ktor-servlet", ktorServlet).apply {
            addMapping("/*")
            isAsyncSupported = true
            multipartConfigElement = MultipartConfigElement("")
        }
    }

    init {
        applicationLifecycle.interceptInitializeApplication {
            setupDefaultHostPages()
            install(TransformationSupport).registerDefaultHandlers()
        }
    }

    override fun start(wait: Boolean) {
        application.environment.log.info("Starting server...") // touch application to ensure initialized
        server.start()
        config.log.info("Server started")

        if (wait) {
            server.server.await()
            config.log.info("Server stopped.")
        }
    }

    override fun stop() {
        server.stop()
        config.log.info("Server stopped.")
        tempDirectory.toFile().deleteRecursively()
    }
}