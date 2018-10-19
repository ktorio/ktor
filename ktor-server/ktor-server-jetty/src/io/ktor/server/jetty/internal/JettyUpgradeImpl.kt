package io.ktor.server.jetty.internal

import io.ktor.http.content.*
import io.ktor.server.servlet.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import org.eclipse.jetty.io.*
import org.eclipse.jetty.server.*
import java.util.concurrent.*
import javax.servlet.http.*
import kotlin.coroutines.*

@Suppress("KDocMissingDocumentation")
@InternalAPI
object JettyUpgradeImpl : ServletUpgrade {
    override suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest, servletResponse: HttpServletResponse,
        engineContext: CoroutineContext, userContext: CoroutineContext
    ) {
        // Jetty doesn't support Servlet API's upgrade so we have to implement our own

        withContext(engineContext) {
            val connection = servletRequest.getAttribute(HttpConnection::class.qualifiedName) as Connection

            // for upgraded connections IDLE timeout should be significantly increased
            connection.endPoint.idleTimeout = TimeUnit.MINUTES.toMillis(60L)

            val inputChannel = ByteChannel(autoFlush = true)
            val reader = EndPointReader(connection.endPoint, engineContext, inputChannel)
            val outputChannel = endPointWriter(connection.endPoint)

            servletRequest.setAttribute(HttpConnection.UPGRADE_CONNECTION_ATTRIBUTE, reader)
            val job = upgrade.upgrade(inputChannel, outputChannel, engineContext, userContext)

            job.invokeOnCompletion {
                connection.close()
            }

            job.join()
        }
    }
}
