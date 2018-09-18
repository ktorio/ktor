package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.util.cio.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.*
import kotlinx.coroutines.io.*
import javax.servlet.*
import javax.servlet.http.*

class BlockingServletApplicationCall(
    application: Application,
    servletRequest: HttpServletRequest,
    servletResponse: HttpServletResponse
) : BaseApplicationCall(application) {
    override val request: ApplicationRequest = BlockingServletApplicationRequest(this, servletRequest)
    override val response: ApplicationResponse = BlockingServletApplicationResponse(this, servletResponse)
}

private class BlockingServletApplicationRequest(
    call: ApplicationCall,
    servletRequest: HttpServletRequest
) : ServletApplicationRequest(call, servletRequest) {

    override fun receiveChannel() = servletRequest.inputStream.toByteReadChannel()
}

private class BlockingServletApplicationResponse(
    call: ApplicationCall,
    servletResponse: HttpServletResponse
) : ServletApplicationResponse(call, servletResponse) {
    override fun createResponseJob(): ReaderJob =
        reader(Unconfined) {
            val buffer = ArrayPool.borrow()
            try {
                writeLoop(buffer, channel, servletResponse.outputStream)
            } finally {
                ArrayPool.recycle(buffer)
            }
        }

    private suspend fun writeLoop(buffer: ByteArray, from: ByteReadChannel, to: ServletOutputStream) {
        while (true) {
            val n = from.readAvailable(buffer)
            if (n < 0) break
            check(n > 0)
            try {
                to.write(buffer, 0, n)
                to.flush()
            } catch (cause: Throwable) {
                throw ChannelWriteException("Failed to write to ServletOutputStream", cause)
            }
        }
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        servletResponse.sendError(501, "Upgrade is not supported in synchronous servlets")
    }
}


