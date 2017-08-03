package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.servlet.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationResponse(call: ServletApplicationCall,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               hostCoroutineContext: CoroutineContext,
                               userCoroutineContext: CoroutineContext,
                               pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit,
                               private val server: Server)
    : ServletApplicationResponse(call, servletRequest, servletResponse,
        hostCoroutineContext, userCoroutineContext, pushImpl) {

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute("org.eclipse.jetty.server.HttpConnection") as HttpConnection
        val inputChannel = EndPointReadChannel(connection.endPoint, server.threadPool)
        val outputChannel = EndPointWriteChannel(connection.endPoint)

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)

        servletResponse.flushBuffer()
        upgrade.upgrade(inputChannel, outputChannel, connection, hostCoroutineContext, userCoroutineContext)
    }
}