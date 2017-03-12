package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.servlet.*
import javax.servlet.http.*

class JettyApplicationCall(application: Application,
                           val server: Server,
                           servletRequest: HttpServletRequest,
                           servletResponse: HttpServletResponse,
                           pool: ByteBufferPool,
                           pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit)
: ServletApplicationCall(application, servletRequest, servletResponse, pool, pushImpl) {

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute("org.eclipse.jetty.server.HttpConnection") as HttpConnection
        val inputChannel = EndPointReadChannel(connection.endPoint, server.threadPool)
        val outputChannel = EndPointWriteChannel(connection.endPoint)

        requestChannelOverride = inputChannel
        responseChannelOverride = outputChannel

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)
        commitHeaders(upgrade)

        servletResponse.flushBuffer()

        upgrade.upgrade(this@JettyApplicationCall, inputChannel, outputChannel, connection)
    }
}