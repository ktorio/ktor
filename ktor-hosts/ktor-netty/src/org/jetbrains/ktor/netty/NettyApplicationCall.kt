package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import io.netty.util.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.util.*
import java.util.concurrent.*

internal class NettyApplicationCall(application: Application,
                                    val context: ChannelHandlerContext,
                                    val httpRequest: HttpRequest,
                                    val bodyConsumed: Boolean,
                                    val urlEncodedParameters: () -> ValuesMap,
                                    val drops: LastDropsCollectorHandler?,
                                    executor: Executor
) : BaseApplicationCall(application, executor) {

    var completed: Boolean = false

    val httpResponse = DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

    override val request = NettyApplicationRequest(httpRequest, bodyConsumed, urlEncodedParameters, context, drops)
    override val response = NettyApplicationResponse(httpRequest, httpResponse, context)
    override val attributes = Attributes()
    override val parameters: ValuesMap get() = request.parameters

    override fun close() {
        completed = true
        ReferenceCountUtil.release(httpRequest)
        drops?.close(context)

        response.finalize()
        request.close()
    }
}