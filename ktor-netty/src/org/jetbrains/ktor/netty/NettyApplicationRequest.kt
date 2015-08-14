package org.jetbrains.ktor.netty

import io.netty.channel.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.interception.*
import java.io.*
import java.nio.charset.*

class NettyApplicationRequest(override val application: Application,
                              val context: ChannelHandlerContext,
                              val request: FullHttpRequest) : ApplicationRequest {
    override val headers by lazy {
        request.headers().toMap({ it.key }, { it.value })
    }

    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(HttpMethod.parse(request.method.name()), request.uri, request.protocolVersion.text())
    }

    override val body: String
        get() {
            val byteBuf = request.content()
            val charsetName = contentType().parameter("charset")
            val charset = charsetName?.let { Charset.forName(it) } ?: Charsets.ISO_8859_1
            return byteBuf.toString(charset)
        }

    override val parameters: Map<String, List<String>> by lazy {
        QueryStringDecoder(request.uri).parameters()
    }

    var async: Boolean = false
    fun continueAsync() {
        this.async = true
    }

    var response: Response? = null
    override val createResponse = Interceptable0<ApplicationResponse> {
        val currentResponse = response
        if (currentResponse != null)
            throw IllegalStateException("There should be only one response for a single request. Make sure you haven't called response more than once.")
        response = Response(context)
        response!!
    }

    inner class Response(val context: ChannelHandlerContext) : ApplicationResponse {
        val response = DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)

        override val header = Interceptable2<String, String, ApplicationResponse> { name, value ->
            response.headers().set(name, value)
            this
        }

        override val status = Interceptable1<Int, ApplicationResponse> { code ->
            response.setStatus(HttpResponseStatus(code, "$code"))
            this
        }

        override val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
            throw UnsupportedOperationException("No known way to stream value $value")
        }

        override val stream = Interceptable1<OutputStream.() -> Unit, ApplicationRequestStatus> { body ->
            val stream = ByteArrayOutputStream()
            stream.body()
            response.content().writeBytes(stream.toByteArray())
            context.write(response)
            context.flush()
            if (async)
                context.close()
            ApplicationRequestStatus.Handled
        }
    }
}