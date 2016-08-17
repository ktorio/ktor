package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    val drops: LastDropsCollectorHandler?
) : BaseApplicationCall(application) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request = NettyApplicationRequest(this, httpRequest, context, drops)
    override val response = NettyApplicationResponse(this, httpRequest, httpResponse, context)

    override fun close() {
        completed = true
        ReferenceCountUtil.release(httpRequest)
        drops?.close(context)

        response.finalize()
        request.close()
    }
}