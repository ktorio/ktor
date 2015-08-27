package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import java.nio.charset.*

class NettyApplicationRequest(val request: FullHttpRequest) : ApplicationRequest {
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


    override val attributes = Attributes()
}