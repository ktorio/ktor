package io.ktor.server.servlet

import io.ktor.cio.*
import io.ktor.content.*
import kotlinx.coroutines.experimental.*
import java.io.*
import javax.servlet.http.*
import kotlin.coroutines.experimental.*

interface ServletUpgrade {
    suspend fun performUpgrade(upgrade: FinalContent.ProtocolUpgrade,
                               servletRequest: HttpServletRequest,
                               servletResponse: HttpServletResponse,
                               hostCoroutineContext: CoroutineContext,
                               userCoroutineContext: CoroutineContext)
}

object DefaultServletUpgrade : ServletUpgrade {
    suspend override fun performUpgrade(upgrade: FinalContent.ProtocolUpgrade,
                                        servletRequest: HttpServletRequest,
                                        servletResponse: HttpServletResponse,
                                        hostCoroutineContext: CoroutineContext,
                                        userCoroutineContext: CoroutineContext) {

        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, upgrade, hostCoroutineContext, userCoroutineContext)
    }
}

// the following types need to be public as they are accessed through reflection

class UpgradeRequest(val response: HttpServletResponse,
                     val upgradeMessage: FinalContent.ProtocolUpgrade,
                     val hostContext: CoroutineContext,
                     val userAppContext: CoroutineContext)

class ServletUpgradeHandler : HttpUpgradeHandler {
    @Volatile
    lateinit var up: UpgradeRequest

    override fun init(webConnection: WebConnection?) {
        if (webConnection == null) {
            throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
        }

        val servletReader = servletReader(webConnection.inputStream)
        val servletWriter = servletWriter(webConnection.outputStream)

        val inputChannel = CIOReadChannelAdapter(servletReader.channel)
        val outputChannel = CIOWriteChannelAdapter(servletWriter.channel)

        val closeable = Closeable {
            servletWriter.channel.close()
            servletReader.cancel()

            runBlocking {
                servletWriter.join()
                servletReader.join()
            }

            webConnection.close()
        }

        launch(up.userAppContext, start = CoroutineStart.UNDISPATCHED) {
            up.upgradeMessage.upgrade(inputChannel, outputChannel, closeable, up.hostContext, up.userAppContext)
        }
    }

    override fun destroy() {
    }
}
