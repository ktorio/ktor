package io.ktor.server.servlet

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import java.io.*
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
    @Suppress("OverridingDeprecatedMember", "DEPRECATION")
    override fun receiveContent(): IncomingContent = BlockingServletIncomingContent(this)
    override fun receiveChannel() = servletRequest.inputStream.toByteReadChannel()
}

private class BlockingServletIncomingContent(
    request: BlockingServletApplicationRequest
) : ServletIncomingContent(request) {
    override fun readChannel(): ByteReadChannel = inputStream().toByteReadChannel()
    override fun inputStream(): InputStream = request.servletRequest.inputStream
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
            } catch (e: IOException) {
                throw ChannelIOException("Failed to write to ServletOutputStream", e)
            }
        }
    }

    override suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade) {
        servletResponse.sendError(501, "Upgrade is not supported in synchronous servlets")
    }
}


