package io.ktor.server.netty

import io.ktor.content.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.response.*
import io.ktor.server.engine.*
import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import kotlin.coroutines.experimental.*

abstract class NettyApplicationResponse(call: NettyApplicationCall,
                                                 protected val context: ChannelHandlerContext,
                                                 protected val engineContext: CoroutineContext,
                                                 protected val userContext: CoroutineContext) : BaseApplicationResponse(call) {

    val responseMessage = CompletableDeferred<Any>()

    @Volatile
    protected var responseMessageSent = false

    internal var responseChannel: ByteReadChannel = EmptyByteReadChannel

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.finish()
        }
    }

    override suspend fun respondOutgoingContent(content: OutgoingContent) {
        try {
            super.respondOutgoingContent(content)
        } catch (t: Throwable) {
            val out = responseChannel as? ByteWriteChannel
            out?.close(t)
            throw t
        } finally {
            val out = responseChannel as? ByteWriteChannel
            out?.close()
        }
    }

    override suspend fun respondFromBytes(bytes: ByteArray) {
        // Note that it shouldn't set HttpHeaders.ContentLength even if we know it here,
        // because it should've been set by commitHeaders earlier
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"

        if (!responseMessageSent) {
            responseChannel = EmptyByteReadChannel
            responseMessage.complete(responseMessage(chunked, bytes))
            responseMessageSent = true
        }
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        val channel = ByteChannel()
        val chunked = headers[HttpHeaders.TransferEncoding] == "chunked"
        sendResponse(chunked, content = channel)
        return channel
    }

    override suspend fun respondNoContent(content: OutgoingContent.NoContent) {
        respondFromBytes(EmptyByteArray)
    }

    protected abstract fun responseMessage(chunked: Boolean, last: Boolean): Any
    protected open fun responseMessage(chunked: Boolean, data: ByteArray): Any = responseMessage(chunked, true)

    internal fun sendResponse(chunked: Boolean = true, content: ByteReadChannel) {
        if (!responseMessageSent) {
            responseChannel = content
            responseMessage.complete(responseMessage(chunked, content.isClosedForRead))
            responseMessageSent = true
        }
    }

    internal fun ensureResponseSent() {
        sendResponse(content = EmptyByteReadChannel)
    }

    internal fun close() {
        val existingChannel = responseChannel
        if (existingChannel is ByteWriteChannel) {
            existingChannel.close(ClosedWriteChannelException("Application response has been closed"))
            responseChannel = EmptyByteReadChannel
        }

        ensureResponseSent()
        // we don't need to suspendAwait() here as it handled in NettyApplicationCall
        // while close only does flush() and doesn't terminate connection
    }

    fun cancel() {
        if (!responseMessageSent) {
            responseChannel = EmptyByteReadChannel
            responseMessage.cancel()
            responseMessageSent = true
        }
    }

    companion object {
        private val EmptyByteArray = ByteArray(0)
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}