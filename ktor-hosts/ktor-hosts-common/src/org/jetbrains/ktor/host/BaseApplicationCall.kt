package org.jetbrains.ktor.host

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.cio.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.request.*
import org.jetbrains.ktor.response.*
import org.jetbrains.ktor.util.*
import java.nio.*

/**
 * Base class for implementing an [ApplicationCall]
 */
abstract class BaseApplicationCall(final override val application: Application) : ApplicationCall {
    final override val attributes = Attributes()

    private var responded = false

    override val receivePipeline = ApplicationReceivePipeline().apply {
        phases.merge(application.receivePipeline.phases)
    }

    override final val sendPipeline = ApplicationSendPipeline().apply {
        phases.merge(application.sendPipeline.phases)
        intercept(ApplicationSendPipeline.Host) {
            if (responded)
                throw IllegalStateException("Response has already been sent")
            val response = subject
            if (response is FinalContent) {
                respondFinalContent(response)
            } else {
                throw IllegalArgumentException("Response pipeline couldn't transform '${response.javaClass}' to the FinalContent")
            }
        }
    }

    protected fun commitHeaders(o: FinalContent) {
        responded = true
        o.status?.let { response.status(it) } ?: response.status() ?: response.status(HttpStatusCode.OK)
        o.headers.forEach { name, values ->
            for (value in values) {
                response.header(name, value)
            }
        }

        val connection = request.headers["Connection"]
        if (connection != null) {
            when {
                connection.equals("close", true) -> response.header("Connection", "close")
                connection.equals("keep-alive", true) -> response.header("Connection", "keep-alive")
            }
        }
    }

    protected open suspend fun respondFinalContent(content: FinalContent) = when (content) {
        is FinalContent.ProtocolUpgrade -> {
            commitHeaders(content)
            respondUpgrade(content)
        }

    // ByteArrayContent is most efficient
        is FinalContent.ByteArrayContent -> {
            // First call user code to acquire bytes, because it could fail
            val bytes = content.bytes()
            // If bytes are fine, commit headers and send data
            commitHeaders(content)
            respondFromBytes(bytes)
        }

    // WriteChannelContent is more efficient than ReadChannelContent
        is FinalContent.WriteChannelContent -> {
            // First set headers
            commitHeaders(content)
            // Retrieve response channel, that might send out headers, so it should go after commitHeaders
            val responseChannel = responseChannel()
            // Call user code to send data
            content.writeTo(responseChannel)
        }

    // Pipe is least efficient
        is FinalContent.ReadChannelContent -> {
            // First call user code to acquire read channel, because it could fail
            val readChannel = content.readFrom()
            // If channel is fine, commit headers and pipe data
            commitHeaders(content)
            respondFromChannel(readChannel)
        }

    // Do nothing, but maintain `when` exhaustiveness
        is FinalContent.NoContent -> { /* no-op */
            commitHeaders(content)
        }
    }

    protected open suspend fun respondFromBytes(bytes: ByteArray) {
        val response = responseChannel()
        response.write(ByteBuffer.wrap(bytes))
    }

    protected open suspend fun respondFromChannel(readChannel: ReadChannel) {
        val writeChannel = responseChannel()
        readChannel.copyTo(writeChannel, bufferPool, 65536)
        readChannel.close()
//        writeChannel.close()
    }

    protected abstract suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade)
    protected abstract suspend fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool

    override val parameters: ValuesMap get() = request.queryParameters
}