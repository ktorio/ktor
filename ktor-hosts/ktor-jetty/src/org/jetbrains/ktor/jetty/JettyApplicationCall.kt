package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.servlet.*
import java.util.concurrent.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationCall(application: Application,
                           serverExecutor: Executor,
                           request: Request,
                           servletRequest: HttpServletRequest,
                           servletResponse: HttpServletResponse,
                           pool: ByteBufferPool,
                           hostContext: CoroutineContext,
                           userAppContext: CoroutineContext)
    : ServletApplicationCall(application, servletRequest, servletResponse, pool, hostContext, userAppContext) {

    override val response: ServletApplicationResponse =
            JettyApplicationResponse(this,
                    servletRequest,
                    servletResponse,
                    hostContext,
                    userAppContext,
                    request,
                    serverExecutor)

}