package io.ktor.server.jetty

import io.ktor.application.*
import io.ktor.server.jetty.internal.*
import io.ktor.server.servlet.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.*

class JettyApplicationCall(
    application: Application,
    request: Request,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse,
    engineContext: CoroutineContext,
    userContext: CoroutineContext
) : AsyncServletApplicationCall(
    application, servletRequest, servletResponse,
    engineContext, userContext, JettyUpgradeImpl
) {

    override val response: JettyApplicationResponse = JettyApplicationResponse(
            this,
            servletRequest,
            servletResponse,
            engineContext,
            userContext,
            request
        )

}