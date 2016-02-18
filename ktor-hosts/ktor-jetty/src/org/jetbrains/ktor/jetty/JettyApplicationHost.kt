package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.servlet.*
import javax.servlet.*
import javax.servlet.http.*

/**
 * [ApplicationHost] implementation for running standalone Jetty Host
 */
class JettyApplicationHost(override val hostConfig: ApplicationHostConfig,
                           val config: ApplicationConfig,
                           val applicationLifecycle: ApplicationLifecycle) : ApplicationHost {

    private val application: Application get() = applicationLifecycle.application

    constructor(hostConfig: ApplicationHostConfig, config: ApplicationConfig)
    : this(hostConfig, config, ApplicationLoader(config))

    constructor(hostConfig: ApplicationHostConfig, config: ApplicationConfig, application: Application)
    : this(hostConfig, config, object : ApplicationLifecycle {
        override val application: Application = application
        override fun dispose() {
        }
    })

    private val server = Server().apply {
        val httpConfig = HttpConfiguration().apply {
            sendServerVersion = false
        }
        val connectionFactory = HttpConnectionFactory(httpConfig)
        val connector = ServerConnector(this, connectionFactory).apply {
            host = hostConfig.host
            port = hostConfig.port
        }
        connectors = arrayOf(connector)
        handler = Handler()
    }


    private val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"));

    inner class Handler() : AbstractHandler() {
        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.characterEncoding = "UTF-8"
            val call = ServletApplicationCall(application, request, response)
            try {
                val contentType = request.contentType
                if (contentType != null && ContentType.parse(contentType).match(ContentType.MultiPart.Any)) {
                    baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                    // TODO someone reported auto-cleanup issues so we have to check it
                }

                application.handle(call)
                baseRequest.isHandled = call.completed
/*
                     {
                        if (!call.asyncStarted) {
                            val asyncContext = baseRequest.startAsync()
                            call.continueAsync(asyncContext)
                        }
                    }
*/
            } catch(ex: Throwable) {
                config.log.error("Application ${application.javaClass} cannot fulfill the request", ex);
                call.respondStatus(HttpStatusCode.InternalServerError)
            }
        }
    }

    public override fun start(wait: Boolean) {
        config.log.info("Starting server...")

        server.start()
        config.log.info("Server running.")
        if (wait) {
            server.join()
            applicationLifecycle.dispose()
            config.log.info("Server stopped.")
        }
    }

    override fun stop() {
        server.stop()
        applicationLifecycle.dispose()
        config.log.info("Server stopped.")
    }
}
