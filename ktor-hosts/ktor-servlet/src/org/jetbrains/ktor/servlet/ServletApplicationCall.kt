package org.jetbrains.ktor.servlet

import kotlinx.coroutines.experimental.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.util.*
import java.io.*
import javax.servlet.http.*

open class ServletApplicationCall(application: Application,
                                  protected val servletRequest: HttpServletRequest,
                                  protected val servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(this, servletRequest, { requestChannelOverride })
    override val response: ServletApplicationResponse = ServletApplicationResponse(this, servletResponse, pushImpl)

    @Volatile
    protected var requestChannelOverride: ReadChannel? = null
    @Volatile
    protected var responseChannelOverride: WriteChannel? = null

    @Volatile
    var completed: Boolean = false

    suspend override fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade) {
        servletResponse.status = upgrade.status?.value ?: HttpStatusCode.SwitchingProtocols.value
        upgrade.headers.flattenEntries().forEach { e ->
            servletResponse.addHeader(e.first, e.second)
        }

        servletResponse.flushBuffer()
        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, this@ServletApplicationCall, upgrade)

//        servletResponse.flushBuffer()

        completed = true
//        servletRequest.asyncContext?.complete() // causes pipeline execution break however it is required for websocket
    }

    private val responseChannel = lazy {
        ServletWriteChannel(servletResponse.outputStream, application.executor)
    }

    override suspend fun responseChannel(): WriteChannel = responseChannelOverride ?: responseChannel.value

    suspend override fun respond(message: Any) {
        super.respond(message)

        if (!completed) {
            completed = true
            request.close()
            if (responseChannel.isInitialized()) {
                responseChannel.value.apply {
                    flush()
                    close()
                }
            } else {
                servletResponse.flushBuffer()
            }
        }
    }

    // the following types need to be public as they are accessed through reflection

    class UpgradeRequest(val response: HttpServletResponse, val call: ServletApplicationCall, val upgradeMessage: FinalContent.ProtocolUpgrade)

    class ServletUpgradeHandler : HttpUpgradeHandler {
        @Volatile
        lateinit var up: UpgradeRequest

        override fun init(wc: WebConnection?) {
            if (wc == null) {
                throw IllegalArgumentException("Upgrade processing requires WebConnection instance")
            }
            val call = up.call

            val inputChannel = ServletReadChannel(wc.inputStream)
            val outputChannel = ServletWriteChannel(wc.outputStream, call.application.executor)

            up.call.requestChannelOverride = inputChannel
            up.call.responseChannelOverride = outputChannel

            runBlocking {
                up.upgradeMessage.upgrade(call, inputChannel, outputChannel, Closeable {
                    wc.close()
                })
            }
        }

        override fun destroy() {
        }

    }
}