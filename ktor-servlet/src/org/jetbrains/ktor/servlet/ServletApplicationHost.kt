package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import javax.servlet.http.*

open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by lazy {
        val servletContext = servletContext
        val parameterNames = servletContext.initParameterNames.toList().filter { it.startsWith("org.jetbrains.ktor") }
        val parameters = parameterNames.toMap({ it.removePrefix("org.jetbrains.") }, { servletContext.getInitParameter(it) })

        val config = ConfigFactory.parseMap(parameters)
        val configPath = "ktor.config"

        val combinedConfig = if (config.hasPath(configPath)) {
            val configStream = servletContext.classLoader.getResourceAsStream(config.getString(configPath))
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
        response.characterEncoding = "UTF-8"
        request.characterEncoding = "UTF-8"

        try {
            val applicationRequest = ServletApplicationRequestContext(application, request, response)
            val requestResult = application.handle(applicationRequest)
            when (requestResult) {
                ApplicationRequestStatus.Handled -> {
                    applicationRequest.close()
                }
                ApplicationRequestStatus.Unhandled -> {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND)
                    applicationRequest.close()
                }
                ApplicationRequestStatus.Asynchronous -> request.startAsync()
            }
        } catch (ex: Throwable) {
            application.config.log.error("ServletApplicationHost cannot service the request", ex)
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
        }
    }

}
