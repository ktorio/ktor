package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.cio.*
import io.ktor.http.content.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.response.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlinx.io.pool.*
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
    final override val pipeline = ApplicationSendPipeline().apply {
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

    protected fun commitHeaders(content: OutgoingContent) {
        responded = true

        var transferEncodingSet = false

        content.status?.let { status(it) } ?: status() ?: status(HttpStatusCode.OK)
        content.headers.forEach { name, values ->
            when (name) {
                HttpHeaders.TransferEncoding -> transferEncodingSet = true
                HttpHeaders.Upgrade -> {
                    if (content !is OutgoingContent.ProtocolUpgrade)
                        throw InvalidHeaderForContent(HttpHeaders.Upgrade, "non-upgrading response")
                    for (value in values)
                        headers.append(name, value, safeOnly = false)
                    return@forEach
                }
            }
            for (value in values)
                headers.append(name, value)
        }

        val contentLength = content.contentLength
        when {
            contentLength != null -> {
                // TODO: What should we do if TransferEncoding was set and length is present?
                headers.append(HttpHeaders.ContentLength, contentLength.toStringFast(), safeOnly = false)
            }
            !transferEncodingSet -> {
                when (content) {
                    is OutgoingContent.ProtocolUpgrade -> { }
                    is OutgoingContent.NoContent -> headers.append(HttpHeaders.ContentLength, "0", safeOnly = false)
                    else -> headers.append(HttpHeaders.TransferEncoding, "chunked", safeOnly = false)
                }
            }
        }

        content.contentType?.let {
            headers.append(HttpHeaders.ContentType, it.toString(), safeOnly = false)
        }

        val connection = call.request.headers[HttpHeaders.Connection]
        if (connection != null) {
            when {
                connection.equals("close", true) -> header("Connection", "close")
                connection.equals("keep-alive", true) -> header("Connection", "keep-alive")
            }
        }
    }

    protected open suspend fun respondOutgoingContent(content: OutgoingContent) {
        when (content) {
            is OutgoingContent.ProtocolUpgrade -> {
                commitHeaders(content)
                return respondUpgrade(content)
            }

        // ByteArrayContent is most efficient
            is OutgoingContent.ByteArrayContent -> {
                // First call user code to acquire bytes, because it could fail
                val bytes = content.bytes()
                // If bytes are fine, commit headers and send data
                commitHeaders(content)
                return respondFromBytes(bytes)
            }

        // WriteChannelContent is more efficient than ReadChannelContent
            is OutgoingContent.WriteChannelContent -> {
                // First set headers
                commitHeaders(content)
                // need to be in external function to keep tail suspend call
                return respondWriteChannelContent(content)
            }

        // Pipe is least efficient
            is OutgoingContent.ReadChannelContent -> {
                // First call user code to acquire read channel, because it could fail
                val readChannel = content.readFrom()
                // If channel is fine, commit headers and pipe data
                commitHeaders(content)
                return respondFromChannel(readChannel)
            }

        // Do nothing, but maintain `when` exhaustiveness
            is OutgoingContent.NoContent -> {
                commitHeaders(content)
                return respondNoContent(content)
            }
        }
    }

    protected open suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        // Do nothing by default
    }

    protected open suspend fun respondWriteChannelContent(content: OutgoingContent.WriteChannelContent) {
        // Retrieve response channel, that might send out headers, so it should go after commitHeaders
        responseChannel().use {
            // Call user code to send data
//            val before = totalBytesWritten
            content.writeTo(this)

            // TODO currently we can't ensure length like that
            // because a joined channel doesn't increment totalBytesWritten
//            headers[HttpHeaders.ContentLength]?.toLong()?.let { length ->
//                val written = totalBytesWritten - before
//                ensureLength(length, written)
//            }
        }
    }

    protected open suspend fun respondFromBytes(bytes: ByteArray) {
        headers[HttpHeaders.ContentLength]?.toLong()?.let { length ->
            ensureLength(length, bytes.size.toLong())
        }

        responseChannel().use {
            withContext(Unconfined) {
                writeFully(bytes)
            }
        }
    }

    protected open suspend fun respondFromChannel(readChannel: ByteReadChannel) {
        responseChannel().use {
            val length = headers[HttpHeaders.ContentLength]?.toLong()
            val copied = withContext(Unconfined) {
                readChannel.copyTo(this, length ?: Long.MAX_VALUE)
            }

            length ?: return@use
            val discarded = readChannel.discard(max = 1)
            ensureLength(length, copied + discarded)
        }
    }

    private fun ensureLength(expected: Long, actual: Long) {
        if (expected < actual) throw BodyLengthIsTooLong(expected)
        if (expected > actual) throw BodyLengthIsTooSmall(expected, actual)
    }

    protected abstract suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade)
    protected abstract suspend fun responseChannel(): ByteWriteChannel
    protected open val bufferPool: ObjectPool<ByteBuffer> get() = KtorDefaultPool

    protected abstract fun setStatus(statusCode: HttpStatusCode)

    override fun push(builder: ResponsePushBuilder) {
        link(builder.url.buildString(), LinkHeader.Rel.Prefetch)
    }

    class ResponseAlreadySentException : IllegalStateException("Response has already been sent")

    class InvalidHeaderForContent(name: String, content: String) : IllegalStateException("Header $name is not allowed for $content")

    class BodyLengthIsTooSmall(expected: Long, actual: Long) : IllegalStateException(
            "Body.size is too small. Body: $actual, Content-Length: $expected"
    )

    class BodyLengthIsTooLong(expected: Long) : IllegalStateException(
            "Body.size is too long. Expected $expected"
    )
}