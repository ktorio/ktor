package io.ktor.jetty

import org.eclipse.jetty.server.*
import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.servlet.*
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