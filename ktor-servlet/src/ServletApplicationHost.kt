package ktor.application

import javax.servlet.http.*
import java.io.*
import javax.servlet.*
import ktor.application.*
import kotlin.properties.Delegates
import javax.naming.InitialContext

open class ServletApplicationHost() : HttpServlet() {
    private val loader: ApplicationLoader by Delegates.lazy {
        val config = ContextConfig(InitialContext())
        val appConfig = ApplicationConfig(config)
        ApplicationLoader(appConfig)
    }

    val application: Application get() = loader.application


    public override fun destroy() {
        loader.dispose()
    }

    protected override fun service(req: HttpServletRequest?, resp: HttpServletResponse?) {
        dispatch(req!!, resp!!)
    }

    private fun dispatch(request: HttpServletRequest, response: HttpServletResponse) {
        response.setCharacterEncoding("UTF-8")
        request.setCharacterEncoding("UTF-8")

        try {
            if (!application.handle(ServletApplicationRequest(application, request, response))) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND)
            }
        }
        catch (ex: Throwable) {
            println(ex.printStackTrace())
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, ex.getMessage())
        }
    }

}
