package org.jetbrains.ktor.servlet

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.pipeline.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.servlet.http.*

internal fun setupUpgradeHelper(request: HttpServletRequest, response: HttpServletResponse, latch: CountDownLatch, call: ServletApplicationCall, upgraded: AtomicBoolean) {
    call.response.pipeline.intercept(RespondPipeline.After) {
        val message = subject.message
        if (message is FinalContent.ProtocolUpgrade) {
            response.status = message.status?.value ?: HttpStatusCode.SwitchingProtocols.value
            message.headers.flattenEntries().forEach { e ->
                response.addHeader(e.first, e.second)
            }

            val handler = request.upgrade(ServletUpgradeHandler::class.java)
            handler.up = UpgradeRequest(latch, response, call, message, this)

            upgraded.set(true)
            latch.countDown()

            pause()
        }
    }
}

class UpgradeRequest(val latch: CountDownLatch, val response: HttpServletResponse, val call: ServletApplicationCall, val upgradeMessage: FinalContent.ProtocolUpgrade, val context: PipelineContext<*>)

class ServletUpgradeHandler : HttpUpgradeHandler {
    @Volatile
    lateinit var up: UpgradeRequest

    override fun init(wc: WebConnection) {
        val call = up.call

        val inputChannel = ServletReadChannel(wc.inputStream)
        val outputChannel = ServletWriteChannel(wc.outputStream)

        call.attributes.put(BaseApplicationCall.RequestChannelOverride, inputChannel)
        call.attributes.put(BaseApplicationCall.ResponseChannelOverride, outputChannel)

        up.upgradeMessage.upgrade(call, up.context, inputChannel, outputChannel)
    }

    override fun destroy() {
        println("d")
    }

}