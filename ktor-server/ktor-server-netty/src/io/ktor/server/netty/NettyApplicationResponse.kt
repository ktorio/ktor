package io.ktor.server.netty

import io.ktor.cio.*
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

internal abstract class NettyApplicationResponse(call: NettyApplicationCall,
                                                 protected val context: ChannelHandlerContext,
                                                 protected val engineContext: CoroutineContext,
                                                 protected val userContext: CoroutineContext) : BaseApplicationResponse(call) {

    internal val responseMessage = CompletableDeferred<Any>()

    @Volatile
    protected var responseMessageSent = false

    internal var responseChannel: ByteReadChannel = EmptyByteReadChannel

    init {
        pipeline.intercept(ApplicationSendPipeline.Engine) {
            call.finish()
        }
    }

    suspend override fun respondOutgoingContent(content: OutgoingContent) {
        try {
            if (content is OutgoingContent.NoContent && HttpHeaders.ContentLength in content.headers) {
                commitHeaders(content)
                sendResponse(false, EmptyByteReadChannel)
            } else {
                super.respondOutgoingContent(content)
            }
        } catch (t: Throwable) {
            val out = responseChannel as? ByteWriteChannel
            out?.close(t)
            throw t
        } finally {
            val out = responseChannel as? ByteWriteChannel
            if (out != null) out.close()
        }
    }

    suspend override fun respondFromBytes(bytes: ByteArray) {
        // Note that it shouldn't set HttpHeaders.ContentLength even if we know it here,
        // because it should've been set by commitHeaders earlier
        sendResponse(chunked = false, content = ByteReadChannel(bytes))
    }

    override suspend fun responseChannel(): ByteWriteChannel {
        val channel = ByteChannel()
        sendResponse(content = channel)
        return channel
    }

    protected abstract fun responseMessage(chunked: Boolean, last: Boolean): Any

    protected final fun sendResponse(chunked: Boolean = true, content: ByteReadChannel) {
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
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}