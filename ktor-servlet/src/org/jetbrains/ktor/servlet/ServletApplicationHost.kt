package org.jetbrains.ktor.servlet

import com.typesafe.config.*
import org.jetbrains.ktor.application.*
import javax.naming.*
import javax.servlet.http.*
import kotlin.properties.*

open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by Delegates.lazy {
        val namingContext = InitialContext()
        val config = ConfigFactory.parseMap(namingContext.getEnvironment() as Map<String, Any>)
        val appConfig = ApplicationConfig(config)
        ApplicationLoader(appConfig)
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
