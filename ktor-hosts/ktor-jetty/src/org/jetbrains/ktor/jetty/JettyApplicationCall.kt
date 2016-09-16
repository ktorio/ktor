package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.servlet.*
import org.jetbrains.ktor.util.*
import javax.servlet.http.*

class JettyApplicationCall(application: Application,
                           val server: Server,
                           servletRequest: HttpServletRequest,
                           servletResponse: HttpServletResponse,
                           pool: ByteBufferPool,
                           pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit)
: ServletApplicationCall(application, servletRequest, servletResponse, pool, pushImpl) {

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute("org.eclipse.jetty.server.HttpConnection") as HttpConnection
        val inputChannel = AbstractConnectionReadChannel(connection.endPoint, server.threadPool)
        val outputChannel = EndPointWriteChannel(connection.endPoint)

        requestChannelOverride = inputChannel
        responseChannelOverride = outputChannel

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)
        servletResponse.status = upgrade.status?.value ?: HttpStatusCode.SwitchingProtocols.value
        upgrade.headers.flattenEntries().forEach { e ->
            servletResponse.addHeader(e.first, e.second)
        }

        servletResponse.flushBuffer()

        upgrade.upgrade(this@JettyApplicationCall, this, inputChannel, outputChannel)
        pause()
    }
}