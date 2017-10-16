package io.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import kotlinx.coroutines.experimental.*
import kotlinx.coroutines.experimental.io.*
import io.ktor.cio.*
import io.ktor.content.*
import io.ktor.host.*
import io.ktor.http.*
import io.ktor.http.HttpHeaders
import io.ktor.response.*
import java.io.*
import kotlin.coroutines.experimental.*

internal abstract class NettyApplicationResponse(call: NettyApplicationCall,
                                        protected val context: ChannelHandlerContext,
                                        protected val hostCoroutineContext: CoroutineContext,
                                        protected val userCoroutineContext: CoroutineContext) : BaseApplicationResponse(call) {

    internal val responseMessage = CompletableDeferred<Any>()

    @Volatile
    protected var responseMessageSent = false

    internal var responseChannel: ByteReadChannel = EmptyByteReadChannel

    init {
        pipeline.intercept(ApplicationSendPipeline.Host) {
            call.finish()
        }
    }

    suspend override fun respondFinalContent(content: FinalContent) {
        try {
            super.respondFinalContent(content)
        } catch (t: Throwable) {
            val out = responseChannel as? ByteWriteChannel
            if (out != null) out.close(t)
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

    override suspend fun responseChannel(): WriteChannel {
        val channel = ByteChannel()
        sendResponse(content = channel)
        return CIOWriteChannelAdapter(channel)
    }

    protected abstract fun responseMessage(chunked: Boolean, last: Boolean): Any

    protected final fun sendResponse(chunked: Boolean = true, content: ByteReadChannel) {
        if (!responseMessageSent) {
            responseChannel = content
            responseMessage.complete(responseMessage(chunked, content.isClosedForRead))
            responseMessageSent = true
        }
    }

    final fun close() {
        sendResponse(content = EmptyByteReadChannel) // we don't need to suspendAwait() here as it handled in NettyApplicationCall
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