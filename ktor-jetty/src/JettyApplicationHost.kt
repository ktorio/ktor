package ktor.application.jetty

import javax.servlet.http.*
import org.eclipse.jetty.server.*
import org.eclipse.jetty.server.handler.*
import org.eclipse.jetty.server.session.*
import java.util.*
import ktor.application.*

/** A Runnable responsible for managing a Jetty server instance.
 */
public class JettyApplicationHost(val config: ApplicationConfig) {
    var server: Server? = null
    val resourceHandlers = ArrayList<ResourceHandler>()
    val loader = ApplicationLoader(config)

    val application: Application get() = loader.application

    inner class Handler() : AbstractHandler() {

        override fun handle(target: String?, baseRequest: Request?, request: HttpServletRequest?, response: HttpServletResponse?) {
            target!!
            baseRequest!!
            request!!
            response!!

            response.setCharacterEncoding("UTF-8")
            val query = request.getQueryString()
            val method = request.getMethod()
            try {
                if (application.handle(ServletApplicationRequest(application, request, response))) {
                    baseRequest.setHandled(true)
                    config.log.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- OK")
                }
                else {
                    for (resourceHandler in resourceHandlers) {
                        resourceHandler.handle(target, baseRequest, request, response)
                        if (baseRequest.isHandled()) {
                            config.log.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- OK @${resourceHandler.getResourceBase()}")
                            break;
                        }
                    }
                }
                if (!baseRequest.isHandled()) {
                    config.log.info("$method -- ${request.getRequestURL()}${if (query != null) "?" + query else ""} -- FAIL")
                }
            }
            catch(ex: Throwable) {
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
        }
        catch (ex: Exception) {
            throw RuntimeException("${config.port} is not a valid port number")
        }
        server = Server(port)

        config.publicDirectories.forEach {
            config.log.info("Attaching resource handler: ${it}")
            val resourceHandler = ResourceHandler()
            resourceHandler.setDirectoriesListed(false)
            resourceHandler.setResourceBase("./${it}")
            resourceHandler.setWelcomeFiles(array("index.html"))
            resourceHandlers.add(resourceHandler)
        }

        val sessionHandler = SessionHandler()
        val sessionManager = HashSessionManager()
        sessionManager.setStoreDirectory(java.io.File("tmp/sessions"))
        sessionHandler.setSessionManager(sessionManager)
        sessionHandler.setHandler(Handler())
        server?.setHandler(sessionHandler)

        server?.start()
        config.log.info("Server running.")
        server?.join()
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
