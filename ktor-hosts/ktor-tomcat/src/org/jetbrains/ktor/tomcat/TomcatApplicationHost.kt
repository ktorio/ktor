package org.jetbrains.ktor.tomcat

import org.apache.catalina.startup.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.servlet.*
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
        setPort(hostConfig.port)
        setHostname(hostConfig.host)
        setBaseDir(tempDirectory.toString())

        val ctx = addContext("", tempDirectory.toString())

        Tomcat.addServlet(ctx, "ktor-servlet", ktorServlet).apply {
            addMapping("/*")
            isAsyncSupported = true
            multipartConfigElement = MultipartConfigElement("")
        }
    }

    override fun start(wait: Boolean) {
        config.log.info("Starting server...")
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