package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.server.session.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.servlet.*
import java.io.*
import java.util.*
import javax.servlet.http.*

/** A Runnable responsible for managing a Jetty server instance.
 */
class JettyApplicationHost(val config: ApplicationConfig) {
    var server: Server? = null
    val loader = ApplicationLoader(config)

    val application: Application get() = loader.application

    inner class Handler() : AbstractHandler() {

        override fun handle(target: String, baseRequest: Request, request: HttpServletRequest, response: HttpServletResponse) {
            response.setCharacterEncoding("UTF-8")
            try {
                if (application.handle(ServletApplicationRequest(application, request, response))) {
                    baseRequest.setHandled(true)
                }
            } catch(ex: Throwable) {
                config.log.warning("dispatch error: ${ex.getMessage()}");
                ex.printStackTrace()
                val out = response.getWriter()
                out?.print(ex.getMessage())
                out?.flush()
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
