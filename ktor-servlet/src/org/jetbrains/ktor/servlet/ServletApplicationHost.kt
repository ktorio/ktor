package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import javax.servlet.http.*
import kotlin.properties.*

open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by Delegates.lazy {
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
            if (!application.handle(ServletApplicationRequest(application, request, response))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            }
        } catch (ex: Throwable) {
            println(ex.printStackTrace())
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
        }
    }

}
