package io.ktor.server.servlet

import io.ktor.http.content.*
import kotlinx.coroutines.experimental.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

interface ServletUpgrade {
    suspend fun performUpgrade(upgrade: OutgoingContent.ProtocolUpgrade,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               engineContext: CoroutineContext,
                               userContext: CoroutineContext)
}

object DefaultServletUpgrade : ServletUpgrade {
    override suspend fun performUpgrade(upgrade: OutgoingContent.ProtocolUpgrade,
                                        servletRequest: HttpServletRequest,
                                        servletResponse: HttpServletResponse,
                                        engineContext: CoroutineContext,
                                        userContext: CoroutineContext) {

        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, upgrade, engineContext, userContext)
    }
}

// the following types need to be public as they are accessed through reflection

class UpgradeRequest(val response: HttpServletResponse,
                     val upgradeMessage: OutgoingContent.ProtocolUpgrade,
                     val engineContext: CoroutineContext,
                     val userContext: CoroutineContext)

class ServletUpgradeHandler : HttpUpgradeHandler {
    @Volatile
    lateinit var up: UpgradeRequest
    private val upgradeJob = Job()

    override fun init(webConnection: WebConnection?) {
        if (webConnection == null) {
            throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
        }

        upgradeJob.invokeOnCompletion {
            webConnection.close()
        }

        val servletReader = servletReader(webConnection.inputStream, parent = upgradeJob)
        val servletWriter = servletWriter(webConnection.outputStream, parent = upgradeJob)

        val inputChannel = servletReader.channel
        val outputChannel = servletWriter.channel

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
