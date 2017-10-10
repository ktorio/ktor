package io.ktor.jetty

import org.eclipse.jetty.server.*
import io.ktor.content.*
import io.ktor.response.*
import io.ktor.servlet.*
import java.util.concurrent.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

class JettyApplicationResponse(call: ServletApplicationCall,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               hostCoroutineContext: CoroutineContext,
                               userCoroutineContext: CoroutineContext,
                               private val baseRequest: Request,
                               private val serverExecutor: Executor)
    : ServletApplicationResponse(call, servletRequest, servletResponse, hostCoroutineContext, userCoroutineContext) {

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute("org.eclipse.jetty.server.HttpConnection") as HttpConnection
        val inputChannel = EndPointReadChannel(connection.endPoint, serverExecutor)
        val outputChannel = EndPointWriteChannel(connection.endPoint)

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)

        servletResponse.flushBuffer()
        upgrade.upgrade(inputChannel, outputChannel, connection, hostCoroutineContext, userCoroutineContext)
    }

    override fun push(builder: ResponsePushBuilder) {
        if (baseRequest.isPushSupported) {
            baseRequest.pushBuilder.apply {
                this.method(builder.method.value)
                this.path(builder.url.encodedPath)
                val query = builder.url.build().substringAfter('?', "").takeIf { it.isNotEmpty() }
                if (query != null) {
                    queryString(query)
                }

                push()
            }
        } else {
            super.push(builder)
        }
    }
}