package org.jetbrains.ktor.jetty

import org.eclipse.jetty.server.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.servlet.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import javax.servlet.http.*

internal fun setupUpgradeHelper(request: HttpServletRequest, response: HttpServletResponse, server: Server, latch: CountDownLatch, call: ServletApplicationCall) {
    call.interceptRespond(0) { obj ->
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

            latch.countDown()
            response.flushBuffer()

            obj.upgrade(call, this, call.request.content.get(), call.response.channel())

            pause()
        }
    }
}
