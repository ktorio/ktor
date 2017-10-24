package io.ktor.server.jetty

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.server.jetty.internal.*
import io.ktor.server.servlet.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationCall(application: Application,
                           request: Request,
                           servletRequest: HttpServletRequest,
                           servletResponse: HttpServletResponse,
                           pool: ByteBufferPool,
                           hostContext: CoroutineContext,
                           userAppContext: CoroutineContext)
    : ServletApplicationCall(application, servletRequest, servletResponse,
        pool, hostContext, userAppContext, JettyUpgradeImpl) {

    override val response: JettyApplicationResponse =
            JettyApplicationResponse(this,
                    servletRequest,
                    servletResponse,
                    hostContext,
                    userAppContext,
                    request)

}