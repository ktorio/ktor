package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import javax.servlet.http.*
import kotlin.properties.*

open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by lazy {
        val servletContext = getServletContext()
        val parameterNames = servletContext.getInitParameterNames().toList().filter { it.startsWith("org.jetbrains.ktor") }
        val parameters = parameterNames.toMap({ it.removePrefix("org.jetbrains.") }, { servletContext.getInitParameter(it) })

        val config = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"

        val combinedConfig = if (config.hasPath(configPath)) {
            val configStream = servletContext.getClassLoader().getResourceAsStream(config.getString(configPath))
            val loadedKtorConfig = ConfigFactory.parseReader(configStream.bufferedReader())
            config.withFallback(loadedKtorConfig)
        } else
            config

        val applicationLog = SL4JApplicationLog("<Application>")
        val applicationConfig = ApplicationConfig(combinedConfig, applicationLog)
        ApplicationLoader(applicationConfig)
    }

    val application: Application get() = loader.application


    public override fun destroy() {
        loader.dispose()
    }

    protected override fun service(request: HttpServletRequest, response: HttpServletResponse) {
        response.setCharacterEncoding("UTF-8")
        request.setCharacterEncoding("UTF-8")

        try {
            val requestResult = application.handle(ServletApplicationRequest(application, request, response))
            when (requestResult) {
                ApplicationRequestStatus.Handled -> {
                    /* do nothing, request is handled */
                }
                ApplicationRequestStatus.Unhandled -> response.sendError(HttpServletResponse.SC_NOT_FOUND)
                ApplicationRequestStatus.Asynchronous -> request.startAsync()
            }
        } catch (ex: Throwable) {
            application.config.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
        }
    }

}
