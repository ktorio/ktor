package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.servlet.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import javax.servlet.http.*

internal fun setupUpgradeHelper(request: HttpServletRequest, response: HttpServletResponse, server: Server, latch: CountDownLatch, call: ServletApplicationCall) {
    call.response.pipeline.intercept(RespondPipeline.After) {
        val obj = subject.message
        if (obj is FinalContent.ProtocolUpgrade) {
            val connection = request.getAttribute("org.eclipse.jetty.server.HttpConnection") as HttpConnection
            val inputChannel = AbstractConnectionReadChannel(connection.endPoint, server.threadPool)
            val outputChannel = EndPointWriteChannel(connection.endPoint)

            call.attributes.put(BaseApplicationCall.RequestChannelOverride, inputChannel)
            call.attributes.put(BaseApplicationCall.ResponseChannelOverride, outputChannel)

            request.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)
            response.status = obj.status?.value ?: HttpStatusCode.SwitchingProtocols.value
            obj.headers.flattenEntries().forEach { e ->
                response.addHeader(e.first, e.second)
            }

            response.flushBuffer()
            latch.countDown()

            obj.upgrade(call, this, inputChannel, outputChannel)

            pause()
        }
    }
}
