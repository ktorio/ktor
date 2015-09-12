package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import java.util.*

class NettyApplicationRequest(private val request: FullHttpRequest) : ApplicationRequest {
    override val headers by lazy {
        ValuesMap.build { request.headers().forEach { append(it.key, it.value) } }
    }

    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(HttpMethod.parse(request.method.name()), request.uri, request.protocolVersion.text())
    }

    override val body: String
        get() {
            val byteBuf = request.content()
            return byteBuf.toString(contentCharset ?: Charsets.ISO_8859_1)
        }

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            QueryStringDecoder(request.uri).parameters().forEach {
                appendAll(it.key, it.value)
            }
            // as far as we have full request we can access request body many times
            if (contentType().match(ContentType.Application.FormUrlEncoded)) {
                appendAll(parseUrlEncodedParameters())
            }
        }
    }


    override val attributes = Attributes()
    override val cookies = NettyRequestCookies(this)
}

private class NettyRequestCookies(val owner: ApplicationRequest) : RequestCookies(owner) {
    override val parsedRawCookies: Map<String, String> by lazy {
        owner.headers["Cookie"]?.fold(HashMap<String, String>()) { acc, e ->
            acc.putAll(ServerCookieDecoder.LAX.decode(owner.header("Cookie") ?: "").toMap({ it.name() }, { it.value() }))
            acc
        } ?: emptyMap<String, String>()
    }
}