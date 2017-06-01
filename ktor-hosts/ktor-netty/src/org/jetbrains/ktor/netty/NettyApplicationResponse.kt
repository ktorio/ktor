package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.handler.stream.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpHeaders
import org.jetbrains.ktor.response.*

internal class NettyApplicationResponse(call: ApplicationCall, val context: ChannelHandlerContext) : BaseApplicationResponse(call) {
    val response = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    @Volatile
    private var responseMessageSent = false

    @Volatile
    private var responseChannel0: HttpContentWriteChannel? = null

    override fun setStatus(statusCode: HttpStatusCode) {
        val cached = responseStatusCache[statusCode.value]

        response.status = cached?.takeIf { cached.reasonPhrase() == statusCode.description }
                ?: HttpResponseStatus(statusCode.value, statusCode.description)
    }

    internal suspend fun responseChannel(): HttpContentWriteChannel {
        sendResponseMessage()

        return if (responseChannel0 == null) {
            val ch = HttpContentWriteChannel(context)
            responseChannel0 = ch
            ch
        } else responseChannel0!!
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

    internal suspend fun sendResponseMessage(chunked: Boolean = true, flush: Boolean = true): ChannelFuture? {
        if (!responseMessageSent) {
            if (chunked)
                setChunked()

            context.channel().attr(NettyHostHttp1Handler.ResponseQueueKey).get()?.await(call)

            // TODO await for response queue
            val f = if (flush) context.writeAndFlush(response) else context.write(response)
            responseMessageSent = true
            return f
        }

        return null
    }

    suspend fun close() {
        sendResponseMessage()
        responseChannel0?.close()
    }

    private fun setChunked() {
        if (responseMessageSent) {
            if (!response.headers().contains(HttpHeaders.TransferEncoding, HttpHeaderValues.CHUNKED, true)) {
                throw IllegalStateException("Already committed")
            }
        }
        if (response.status().code() != HttpStatusCode.SwitchingProtocols.value) {
            HttpUtil.setTransferEncodingChunked(response, true)
        }
    }

    companion object {
        val responseStatusCache = HttpStatusCode.allStatusCodes.associateBy({ it.value }, { HttpResponseStatus.valueOf(it.value) })
    }
}