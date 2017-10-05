package io.ktor.host

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.response.*
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
    }

    protected abstract suspend fun respondUpgrade(upgrade: FinalContent.ProtocolUpgrade)
    protected abstract suspend fun responseChannel(): WriteChannel
    protected open val bufferPool: ByteBufferPool get() = NoPool

    protected abstract fun setStatus(statusCode: HttpStatusCode)

    override fun push(builder: ResponsePushBuilder) {
        link(builder.url.build(), LinkHeader.Rel.Prefetch)
    }
}