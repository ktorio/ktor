package io.ktor.server.jetty.internal

import io.ktor.content.*
import io.ktor.server.jetty.*
import io.ktor.server.servlet.*
import kotlinx.coroutines.experimental.*
import org.eclipse.jetty.server.*
import java.util.concurrent.Executor
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

object JettyUpgradeImpl : ServletUpgrade {
    suspend override fun performUpgrade(upgrade: FinalContent.ProtocolUpgrade, servletRequest: HttpServletRequest, servletResponse: HttpServletResponse, hostCoroutineContext: CoroutineContext, userCoroutineContext: CoroutineContext) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute(HttpConnection::class.qualifiedName) as HttpConnection
        val inputChannel = EndPointReadChannel(connection.endPoint, Executor {
            launch(hostCoroutineContext) {
                it.run()
            }
        })
        val outputChannel = EndPointWriteChannel(connection.endPoint)

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, inputChannel)
        upgrade.upgrade(inputChannel, outputChannel, connection, hostCoroutineContext, userCoroutineContext)
    }
}