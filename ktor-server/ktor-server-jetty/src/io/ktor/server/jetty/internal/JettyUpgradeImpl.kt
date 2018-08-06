package io.ktor.server.jetty.internal

import io.ktor.http.content.*
import io.ktor.server.servlet.*
import kotlinx.coroutines.experimental.io.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

object JettyUpgradeImpl : ServletUpgrade {
    override suspend fun performUpgrade(
            upgrade: OutgoingContent.ProtocolUpgrade,
            servletRequest: HttpServletRequest, servletResponse: HttpServletResponse,
            engineContext: CoroutineContext, userContext: CoroutineContext
    ) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        val connection = servletRequest.getAttribute(HttpConnection::class.qualifiedName) as Connection
        val inputChannel = ByteChannel(autoFlush = true)
        val reader = EndPointReader(connection.endPoint, engineContext, inputChannel)
        val outputChannel = endPointWriter(connection.endPoint)

        servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, reader)
        val job = upgrade.upgrade(inputChannel, outputChannel, engineContext, userContext)
        job.invokeOnCompletion {
            connection.close()
        }
    }
}