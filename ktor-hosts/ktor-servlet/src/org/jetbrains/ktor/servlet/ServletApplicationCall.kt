package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import javax.servlet.*
import javax.servlet.http.*

open class ServletApplicationCall(application: Application,
                                  protected val servletRequest: HttpServletRequest,
                                  protected val servletResponse: HttpServletResponse,
                                  override val bufferPool: ByteBufferPool,
                                  pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit) : BaseApplicationCall(application) {

    override val request: ServletApplicationRequest = ServletApplicationRequest(servletRequest, { requestChannelOverride })
    override val response: ServletApplicationResponse = ServletApplicationResponse(this, respondPipeline, servletResponse, pushImpl, { responseChannel() })

    @Volatile
    protected var requestChannelOverride: ReadChannel? = null
    @Volatile
    protected var responseChannelOverride: WriteChannel? = null

    private val asyncContext: AsyncContext?
        get() = servletRequest.asyncContext

    @Deprecated("Always true")
    val asyncStarted: Boolean
        get() = asyncContext != null

    @Volatile
    var completed: Boolean = false

    @Volatile
    private var upgraded: Boolean = false

    override fun PipelineContext<*>.handleUpgrade(upgrade: ProtocolUpgrade) {
        servletResponse.status = upgrade.status?.value ?: HttpStatusCode.SwitchingProtocols.value
        upgrade.headers.flattenEntries().forEach { e ->
            servletResponse.addHeader(e.first, e.second)
        }

        servletResponse.flushBuffer()
        val handler = servletRequest.upgrade(ServletUpgradeHandler::class.java)
        handler.up = UpgradeRequest(servletResponse, this@ServletApplicationCall, upgrade, this)

        upgraded = true
        servletResponse.flushBuffer()

        ensureCompleted()
        pause()
    }

    private val responseChannel = lazy {
        ServletWriteChannel(servletResponse.outputStream)
    }

    override fun responseChannel(): WriteChannel = responseChannelOverride ?: responseChannel.value

    @Synchronized
    override fun close() {
        ensureCompleted()
    }

    @Synchronized
    @Deprecated("Request processing is always async. Does nothing")
    fun ensureAsync() {
        requireNotNull(asyncContext)
    }

    private fun ensureCompleted() {
        if (!completed) {
            completed = true

            try {
                request.close()
                if (responseChannel.isInitialized()) {
                    responseChannel.value.close()
                }
            } finally {
                asyncContext?.complete()
            }
        }
    }

    // the following types need to be public as they are accessed through reflection

    class UpgradeRequest(val response: HttpServletResponse, val call: ServletApplicationCall, val upgradeMessage: ProtocolUpgrade, val context: PipelineContext<*>)

    class ServletUpgradeHandler : HttpUpgradeHandler {
        @Volatile
        lateinit var up: UpgradeRequest

        override fun init(wc: WebConnection) {
            val call = up.call

            val inputChannel = ServletReadChannel(wc.inputStream)
            val outputChannel = ServletWriteChannel(wc.outputStream)

            up.call.requestChannelOverride = inputChannel
            up.call.responseChannelOverride = outputChannel

            up.upgradeMessage.upgrade(call, up.context, inputChannel, outputChannel)
        }

        override fun destroy() {
        }

    }
}