package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.servlet.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationCall(application: Application,
                           val server: Server,
                           servletRequest: HttpServletRequest,
                           servletResponse: HttpServletResponse,
                           pool: ByteBufferPool,
                           pushImpl: (ResponsePushBuilder) -> Boolean,
                           hostContext: CoroutineContext,
                           userAppContext: CoroutineContext)
    : ServletApplicationCall(application, servletRequest, servletResponse, pool, pushImpl, hostContext, userAppContext) {

    override val response: ServletApplicationResponse = JettyApplicationResponse(this, servletRequest, servletResponse, hostContext, userAppContext, pushImpl, server)

}