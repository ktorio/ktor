package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import java.nio.*

/**
 * Base class for implementing an [ApplicationResponse]
 */
abstract class BaseApplicationResponse(override val call: ApplicationCall) : ApplicationResponse {
    private var _status: HttpStatusCode? = null

    override val cookies by lazy { ResponseCookies(this, call.request.origin.scheme == "https") }

    override fun status() = _status
    override fun status(value: HttpStatusCode) {
        _status = value
        setStatus(value)
    }

    private var responded = false
    override final val pipeline = ApplicationSendPipeline().apply {
        merge(call.application.sendPipeline)
        intercept(ApplicationSendPipeline.Engine) {
            if (responded)
                throw ResponseAlreadySentException()
            val response = subject
            if (response is OutgoingContent) {
                respondOutgoingContent(response)
            } else {
                throw IllegalArgumentException("Response pipeline couldn't transform '${response.javaClass}' to the OutgoingContent")
            }
        }
    }

    protected fun commitHeaders(o: OutgoingContent) {
        responded = true
        o.status?.let { status(it) } ?: status() ?: status(HttpStatusCode.OK)
        o.headers.forEach { name, values ->
            for (value in values) {
                header(name, value)
            }
        }

        val connection = call.request.headers["Connection"]
        if (connection != null) {
            when {
                connection.equals("close", true) -> header("Connection", "close")
                connection.equals("keep-alive", true) -> header("Connection", "keep-alive")
            }
        }
    }

    protected open suspend fun respondOutgoingContent(content: OutgoingContent) = when (content) {
        is OutgoingContent.ProtocolUpgrade -> {
            commitHeaders(content)
            respondUpgrade(content)
        }

    // ByteArrayContent is most efficient
        is OutgoingContent.ByteArrayContent -> {
            // First call user code to acquire bytes, because it could fail
            val bytes = content.bytes()
            // If bytes are fine, commit headers and send data
            commitHeaders(content)
            respondFromBytes(bytes)
        }

    // WriteChannelContent is more efficient than ReadChannelContent
        is OutgoingContent.WriteChannelContent -> {
            // First set headers
            commitHeaders(content)
            // Retrieve response channel, that might send out headers, so it should go after commitHeaders
            val responseChannel = responseChannel()
            // Call user code to send data
            content.writeTo(responseChannel)
        }

    // Pipe is least efficient
        is OutgoingContent.ReadChannelContent -> {
            // First call user code to acquire read channel, because it could fail
            val readChannel = content.readFrom()
            // If channel is fine, commit headers and pipe data
            commitHeaders(content)
            respondFromChannel(readChannel)
        }

    // Do nothing, but maintain `when` exhaustiveness
        is OutgoingContent.NoContent -> { /* no-op */
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
        writeChannel.flush()
        readChannel.close()
    }

    protected abstract suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade)
    protected abstract suspend fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool

    protected abstract fun setStatus(statusCode: HttpStatusCode)

    override fun push(builder: ResponsePushBuilder) {
        link(builder.url.build(), LinkHeader.Rel.Prefetch)
    }

    class ResponseAlreadySentException : IllegalStateException("Response has already been sent")
}