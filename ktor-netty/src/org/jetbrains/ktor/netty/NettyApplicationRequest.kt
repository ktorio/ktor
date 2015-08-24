package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import java.nio.charset.*

class NettyApplicationRequest(val request: FullHttpRequest) : ApplicationRequest {
    override val headers by lazy {
        ValuesMap.build { request.headers().forEach { append(it.key, it.value) } }
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

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            QueryStringDecoder(request.uri).parameters().forEach {
                appendAll(it.key, it.value)
            }
        }
    }


    override val attributes = Attributes()
}