package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*

internal class NettyApplicationCall(application: Application,
                           val context: ChannelHandlerContext,
                           val httpRequest: HttpRequest,
                           val readHandler: BodyHandlerChannelAdapter
                           ) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request: ApplicationRequest = NettyApplicationRequest(httpRequest, readHandler)
    override val response: ApplicationResponse = NettyApplicationResponse(httpRequest, httpResponse, context)
    override val attributes = Attributes()
    override val parameters: ValuesMap get() = request.parameters

    init {
        HttpHeaders.setTransferEncodingChunked(httpResponse)
    }

    override fun close() {
        completed = true
        (response as NettyApplicationResponse).finalize()
    }
}