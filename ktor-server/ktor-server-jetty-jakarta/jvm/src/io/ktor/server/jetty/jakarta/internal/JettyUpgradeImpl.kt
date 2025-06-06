package io.ktor.server.jetty.jakarta.internal

import io.ktor.http.content.OutgoingContent
import io.ktor.server.jetty.jakarta.JettyWebsocketConnection
import io.ktor.server.jetty.jakarta.bufferPool
import io.ktor.server.servlet.jakarta.ServletUpgrade
import io.ktor.util.*
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.withContext
import org.eclipse.jetty.io.Connection
import org.eclipse.jetty.server.HttpConnection
import java.util.concurrent.Executor
import kotlin.coroutines.CoroutineContext

@InternalAPI
public object JettyUpgradeImpl : ServletUpgrade {
    private val sameThreadExecutor = Executor { it.run() }

    override suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ) {
        val connection = servletRequest.getAttribute(HttpConnection::class.qualifiedName) as Connection
        val endPoint = connection.endPoint

        withContext(engineContext + CoroutineName("jetty-upgrade")) {
            val wsConnection = JettyWebsocketConnection(
                endPoint,
                bufferPool,
                coroutineContext,
                sameThreadExecutor
            )
            upgrade.upgradeAndAwait(wsConnection, userContext)
        }
    }
}
