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
                                  override val pool: ByteBufferPool,
                                  val onAsyncStartedUnderLock: () -> Unit,
                                  pushImpl: (ApplicationCall, ResponsePushBuilder.() -> Unit, () -> Unit) -> Unit) : BaseApplicationCall(application) {

    override val request: ApplicationRequest = ServletApplicationRequest(this, { ensureAsync() }, servletRequest, { requestChannelOverride })
    override val response: ApplicationResponse = ServletApplicationResponse(this, respondPipeline, servletResponse, pushImpl, { responseChannelOverride })

    @Volatile
    protected var requestChannelOverride: ReadChannel? = null
    @Volatile
    protected var responseChannelOverride: WriteChannel? = null

    @Volatile
    private var asyncContext: AsyncContext? = null

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
        onAsyncStartedUnderLock()

        pause()
    }

    override fun responseChannel(): WriteChannel = responseChannelOverride ?: response.channel()

    @Synchronized
    override fun close() {
        if (!completed) {
            completed = true
            asyncContext?.complete()
        }
    }

    @Synchronized
    fun ensureAsync() {
        if (!asyncStarted && !upgraded) {
            startAsync()
        }
    }

    private fun startAsync() {
        require(this.asyncContext == null) { "You can't reassign asyncContext" }

        asyncContext = servletRequest.startAsync(servletRequest, servletResponse)
        // asyncContext.timeout = ?

        onAsyncStartedUnderLock()
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