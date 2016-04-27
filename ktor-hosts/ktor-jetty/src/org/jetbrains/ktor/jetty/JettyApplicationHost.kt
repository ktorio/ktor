package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.servlet.*
import java.util.concurrent.*
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

    override val executor = Executor { command -> server.threadPool.execute(command) }

    private val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"));

    inner class Handler() : AbstractHandler() {
        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.characterEncoding = "UTF-8"
            val call = ServletApplicationCall(application, request, response, executor)
            try {
                val contentType = request.contentType
                if (contentType != null && ContentType.parse(contentType).match(ContentType.MultiPart.Any)) {
                    baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                    // TODO someone reported auto-cleanup issues so we have to check it
                }

                val future = call.executeOn(executor, application)
                val pipelineState = future.get()!!
                when (pipelineState) {
                    PipelineState.Succeeded -> baseRequest.isHandled = call.completed
                    PipelineState.Executing -> {
                        baseRequest.isHandled = true
                        // TODO how do we report 404 if async or pass to the next handler?

                        call.ensureAsync()
                    }
                    PipelineState.Failed -> baseRequest.isHandled = true
                }
            } catch(ex: Throwable) {
                config.log.error("Application ${application.javaClass} cannot fulfill the request", ex);
                call.executionMachine.runBlockWithResult {
                    call.respond(HttpStatusCode.InternalServerError)
                }
            }
        }
    }

    override fun start(wait: Boolean) {
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
