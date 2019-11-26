/*
 * Copyright 2014-2019 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.server.engine

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.response.*
import io.ktor.util.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import io.ktor.utils.io.pool.*
import kotlinx.coroutines.*
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
    }

    /**
     * Commit header values and status and pass them to the underlying engine
     */
    protected fun commitHeaders(content: OutgoingContent) {
        if (responded)
            throw BaseApplicationResponse.ResponseAlreadySentException()
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
                    is OutgoingContent.ProtocolUpgrade -> {
                    }
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

    /**
     * Process response outgoing [content]
     */
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

    /**
     * Process response with no content
     */
    protected open suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        // Do nothing by default
    }

    /**
     * Process response [content] using [OutgoingContent.WriteChannelContent.writeTo].
     */
    protected open suspend fun respondWriteChannelContent(content: OutgoingContent.WriteChannelContent) {
        // Retrieve response channel, that might send out headers, so it should go after commitHeaders
        responseChannel().use {
            // Call user code to send data
//            val before = totalBytesWritten
            try {
                withContext(Dispatchers.IO) {
                    content.writeTo(this@use)
                }
            } catch (closed: ClosedWriteChannelException) {
                throw ChannelWriteException(exception = closed)
            }

            // TODO currently we can't ensure length like that
            // because a joined channel doesn't increment totalBytesWritten
//            headers[HttpHeaders.ContentLength]?.toLong()?.let { length ->
//                val written = totalBytesWritten - before
//                ensureLength(length, written)
//            }
        }
    }

    /**
     * Respond with [bytes] content
     */
    protected open suspend fun respondFromBytes(bytes: ByteArray) {
        headers[HttpHeaders.ContentLength]?.toLong()?.let { length ->
            ensureLength(length, bytes.size.toLong())
        }

        responseChannel().use {
            withContext(Dispatchers.Unconfined) {
                writeFully(bytes)
            }
        }
    }

    /**
     * Respond from [readChannel]
     */
    protected open suspend fun respondFromChannel(readChannel: ByteReadChannel) {
        responseChannel().use {
            val length = headers[HttpHeaders.ContentLength]?.toLong()
            val copied = withContext(Dispatchers.Unconfined) {
                readChannel.copyTo(this@use, length ?: Long.MAX_VALUE)
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

    /**
     * Process upgrade response
     */
    protected abstract suspend fun respondUpgrade(upgrade: OutgoingContent.ProtocolUpgrade)

    /**
     * Get response output channel
     */
    protected abstract suspend fun responseChannel(): ByteWriteChannel

    /**
     * ByteBuffer pool
     */
    @Deprecated(
        "Avoid specifying pools or use KtorDefaultPool instead.",
        ReplaceWith("KtorDefaultPool", "io.ktor.util.cio.KtorDefaultPool"),
        level = DeprecationLevel.ERROR
    )
    protected open val bufferPool: ObjectPool<ByteBuffer>
        get() = KtorDefaultPool

    /**
     * Set underlying engine's response status
     */
    protected abstract fun setStatus(statusCode: HttpStatusCode)

    override fun push(builder: ResponsePushBuilder) {
        link(builder.url.buildString(), LinkHeader.Rel.Prefetch)
    }

    /**
     * Thrown when there was already response sent but we are trying to respond again
     */
    class ResponseAlreadySentException : IllegalStateException("Response has already been sent")

    /**
     * [OutgoingContent] is trying to set some header that is not allowed for this content type.
     * For example, only upgrade content can set `Upgrade` header.
     */
    class InvalidHeaderForContent(
        private val name: String, private val content: String
    ) : IllegalStateException("Header $name is not allowed for $content"),
        CopyableThrowable<InvalidHeaderForContent> {
        override fun createCopy(): InvalidHeaderForContent? = InvalidHeaderForContent(name, content).also {
            it.initCause(this)
        }

    }

    /**
     * Content's actual body size doesn't match the provided one in `Content-Length` header
     */
    class BodyLengthIsTooSmall(
        private val expected: Long, private val actual: Long
    ) : IllegalStateException("Body.size is too small. Body: $actual, Content-Length: $expected"),
        CopyableThrowable<BodyLengthIsTooSmall> {
        override fun createCopy(): BodyLengthIsTooSmall? = BodyLengthIsTooSmall(expected, actual).also {
            it.initCause(this)
        }
    }

    /**
     * Content's actual body size doesn't match the provided one in `Content-Length` header
     */
    class BodyLengthIsTooLong(private val expected: Long) : IllegalStateException(
        "Body.size is too long. Expected $expected"
    ), CopyableThrowable<BodyLengthIsTooLong> {
        override fun createCopy(): BodyLengthIsTooLong? = BodyLengthIsTooLong(expected).also {
            it.initCause(this)
        }

    }

    companion object {
        /**
         * Attribute key to access engine's response instance.
         * This is engine internal API and should be never used by end-users
         * unless you are writing your own engine implementation
         */
        @EngineAPI
        val EngineResponseAtributeKey = AttributeKey<BaseApplicationResponse>("EngineResponse")

        /**
         * Install an application-wide send pipeline interceptor into [ApplicationSendPipeline.Engine] phase
         * to start response object processing via [respondOutgoingContent]
         */
        @EngineAPI
        fun setupSendPipeline(sendPipeline: ApplicationSendPipeline) {
            sendPipeline.intercept(ApplicationSendPipeline.Engine) { response ->
                if (response !is OutgoingContent) {
                    throw IllegalArgumentException("Response pipeline couldn't transform '${response.javaClass}' to the OutgoingContent")
                }

                val call = call
                val callResponse =
                    call.response as? BaseApplicationResponse ?: call.attributes[EngineResponseAtributeKey]

                callResponse.respondOutgoingContent(response)
            }
        }
    }
}
