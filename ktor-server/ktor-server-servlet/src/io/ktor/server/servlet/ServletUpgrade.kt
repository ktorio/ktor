package io.ktor.server.servlet

import io.ktor.http.content.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import javax.servlet.http.*
import kotlin.coroutines.*

interface ServletUpgrade {
    suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    )
}

object DefaultServletUpgrade : ServletUpgrade {
    override suspend fun performUpgrade(
        upgrade: OutgoingContent.ProtocolUpgrade,
        servletRequest: HttpServletRequest,
        servletResponse: HttpServletResponse,
        engineContext: CoroutineContext,
        userContext: CoroutineContext
    ) {

        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        val disableAsyncInput = servletRequest.servletContext?.serverInfo
            ?.contains("tomcat", ignoreCase = true) == true

        handler.up = UpgradeRequest(servletResponse, upgrade, engineContext, userContext, disableAsyncInput)
    }
}

// the following types need to be public as they are accessed through reflection

class UpgradeRequest(
    val response: HttpServletResponse,
    val upgradeMessage: OutgoingContent.ProtocolUpgrade,
    val engineContext: CoroutineContext,
    val userContext: CoroutineContext,
    val disableAsyncInput: Boolean
)

class ServletUpgradeHandler : HttpUpgradeHandler, CoroutineScope {
    @Volatile
    lateinit var up: UpgradeRequest
    private val upgradeJob = Job()

    override val coroutineContext: CoroutineContext
        get() = upgradeJob

    override fun init(webConnection: WebConnection?) {
        if (webConnection == null) {
            throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
        }

        upgradeJob.invokeOnCompletion {
            webConnection.close()
        }

        val inputChannel = when {
            up.disableAsyncInput -> webConnection.inputStream.toByteReadChannel(
                context = up.userContext,
                parent = upgradeJob
            )
            else -> servletReader(webConnection.inputStream).channel
        }

        val outputChannel = servletWriter(webConnection.outputStream).channel

        launch(up.userContext, start = CoroutineStart.UNDISPATCHED) {
            val job = up.upgradeMessage.upgrade(inputChannel, outputChannel, up.engineContext, up.userContext)

            job.invokeOnCompletion(onCancelling = true) {
                upgradeJob.cancel(it)
            }
        }
    }

    override fun destroy() {
        upgradeJob.cancel(CancellationException("Upgraded WebConnection destroyed"))
    }
}
