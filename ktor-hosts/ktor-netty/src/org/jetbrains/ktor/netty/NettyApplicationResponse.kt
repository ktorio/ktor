package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.response.*

internal class NettyApplicationResponse(call: ApplicationCall, responsePipeline: RespondPipeline, val context: ChannelHandlerContext) : BaseApplicationResponse(call, responsePipeline) {
    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    @Volatile
    private var responseMessageSent = false

    internal var chunked = true

    override fun setStatus(statusCode: HttpStatusCode) {
        val cached = responseStatusCache[statusCode.value]

        response.status = cached?.takeIf { cached.reasonPhrase() == statusCode.description }
                ?: HttpResponseStatus(statusCode.value, statusCode.description)
    }

    internal val responseChannel = lazy {
        sendResponseMessage()
        HttpContentWriteChannel(context)
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            if (responseMessageSent)
                throw UnsupportedOperationException("Headers can no longer be set because response was already completed")
            response.headers().add(name, value)
        }

        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    internal fun sendResponseMessage(): ChannelFuture? {
        if (!responseMessageSent) {
            setChunked()
            val f = context.writeAndFlush(response)
            responseMessageSent = true
            return f
        }

        return null
    }

    fun close() {
        sendResponseMessage()
        if (responseChannel.isInitialized()) {
            responseChannel.value.close()
        }
    }

    private fun setChunked() {
        if (chunked) {
            if (responseMessageSent) {
                if (!response.headers().contains(HttpHeaders.TransferEncoding, HttpHeaderValues.CHUNKED, true)) {
                    throw IllegalStateException("Already committed")
                }
            }
            if (response.status().code() != HttpStatusCode.SwitchingProtocols.value) {
                HttpUtil.setTransferEncodingChunked(response, true)
            }
        }
    }

    companion object {
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}