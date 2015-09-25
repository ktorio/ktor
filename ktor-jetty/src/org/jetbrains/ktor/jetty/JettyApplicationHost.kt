package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.server.session.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.servlet.*
import java.io.*
import javax.servlet.*
import javax.servlet.http.*

/** A Runnable responsible for managing a Jetty server instance.
 */
class JettyApplicationHost(val config: ApplicationConfig) {
    var server: Server? = null
    val loader = ApplicationLoader(config)

    val application: Application get() = loader.application
    val MULTI_PART_CONFIG = MultipartConfigElement(System.getProperty("java.io.tmpdir"));

    inner class Handler() : AbstractHandler() {

        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.characterEncoding = "UTF-8"
            try {
                val appRequest = ServletApplicationRequestContext(application, request, response)
                val contentType = request.contentType
                if (contentType != null && ContentType.parse(contentType).match(ContentType.MultiPart.Any)) {
                    baseRequest.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, MULTI_PART_CONFIG)
                    // TODO someone reported auto-cleanup issues so we have to check it
                }

                val requestResult = application.handle(appRequest)
                when (requestResult) {
                    ApplicationRequestStatus.Handled -> baseRequest.isHandled = true
                    ApplicationRequestStatus.Unhandled -> baseRequest.isHandled = false
                    ApplicationRequestStatus.Asynchronous -> {
                        val asyncContext = baseRequest.startAsync()
                        appRequest.continueAsync(asyncContext)
                    }
                }
            } catch(ex: Throwable) {
                config.log.error("Application ${application.javaClass} cannot fulfill the request", ex);
            }
        }
    }

    public fun start() {
        config.log.info("Starting server...")

        var port: Int
        try {
            port = config.port.toInt()
        } catch (ex: Exception) {
            throw RuntimeException("${config.port} is not a valid port number")
        }

        server = Server().apply {
            val httpConfig = HttpConfiguration().apply {
                sendServerVersion = false
            }
            val connectionFactory = HttpConnectionFactory(httpConfig)
            val connector = ServerConnector(this, connectionFactory).apply {
                setPort(port)
            }
            connectors = arrayOf(connector)
        }

        config.publicDirectories.forEach { path ->
            config.log.info("Attaching resource handler: $path")
            val resourceHandler = ResourceHandler()
            resourceHandler.isDirectoriesListed = false
            resourceHandler.resourceBase = "./$path"
            resourceHandler.welcomeFiles = arrayOf("index.html")
            //TODO: resourceHandlers.add(resourceHandler)
        }

        val sessionHandler = SessionHandler()
        val sessionManager = HashSessionManager()
        sessionManager.storeDirectory = File("tmp/sessions")
        sessionHandler.sessionManager = sessionManager
        sessionHandler.handler = Handler()
        server?.handler = sessionHandler

        server?.start()
        config.log.info("Server running.")
        server?.join()
        config.log.info("Server stopped.")
    }

    public fun stop() {
        if (server != null) {
            server?.stop()
            server = null
        }
    }

    public fun restart() {
        this.stop()
        this.start()
    }

}
