package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.server.session.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.servlet.*
import java.io.*
import javax.servlet.http.*

/** A Runnable responsible for managing a Jetty server instance.
 */
class JettyApplicationHost(val config: ApplicationConfig) {
    var server: Server? = null
    val loader = ApplicationLoader(config)

    val application: Application get() = loader.application

    inner class Handler() : AbstractHandler() {

        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.characterEncoding = "UTF-8"
            try {
                val appRequest = ServletApplicationRequest(application, request, response)
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
        server = Server(port)

        config.publicDirectories.forEach {
            config.log.info("Attaching resource handler: ${it}")
            val resourceHandler = ResourceHandler()
            resourceHandler.setDirectoriesListed(false)
            resourceHandler.setResourceBase("./${it}")
            resourceHandler.setWelcomeFiles(arrayOf("index.html"))
            //TODO: resourceHandlers.add(resourceHandler)
        }

        val sessionHandler = SessionHandler()
        val sessionManager = HashSessionManager()
        sessionManager.setStoreDirectory(File("tmp/sessions"))
        sessionHandler.setSessionManager(sessionManager)
        sessionHandler.setHandler(Handler())
        server?.setHandler(sessionHandler)

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
