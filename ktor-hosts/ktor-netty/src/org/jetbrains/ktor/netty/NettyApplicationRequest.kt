package org.jetbrains.ktor.netty

import io.netty.handler.codec.http.*
import io.netty.handler.codec.http.cookie.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.content.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.HttpMethod
import org.jetbrains.ktor.nio.*
import org.jetbrains.ktor.util.*
import java.io.*
import java.util.*

internal class NettyApplicationRequest(private val request: HttpRequest, private val requestBodyChannel: AsyncReadChannel, val urlEncodedParameters: () -> ValuesMap) : ApplicationRequest {
    override val headers by lazy {
        ValuesMap.build(caseInsensitiveKey = true) { request.headers().forEach { append(it.key, it.value) } }
    }

    override val requestLine: HttpRequestLine by lazy {
        HttpRequestLine(HttpMethod.parse(request.method.name()), request.uri, request.protocolVersion.text())
    }

    override val parameters: ValuesMap by lazy {
        ValuesMap.build {
            QueryStringDecoder(request.uri).parameters().forEach {
                appendAll(it.key, it.value)
            }
            appendAll(urlEncodedParameters())
        }
    }

    override val content: RequestContent = object : RequestContent(this) {
        override fun getMultiPartData(): MultiPartData = TODO("Not yet implemented") //NettyMultiPartData(this@NettyApplicationRequest, request)
        override fun getInputStream(): InputStream = TODO("Not yet implemented") //ByteBufInputStream(request.content())
        override fun getReadChannel(): AsyncReadChannel = requestBodyChannel
    }

    override val cookies : RequestCookies = NettyRequestCookies(this)
}

private class NettyRequestCookies(val owner: ApplicationRequest) : RequestCookies(owner) {
    override val parsedRawCookies: Map<String, String> by lazy {
        owner.headers.getAll("Cookie")?.fold(HashMap<String, String>()) { acc, e ->
            val cookieHeader = owner.header("Cookie") ?: ""
            acc.putAll(ServerCookieDecoder.LAX.decode(cookieHeader).associateBy({ it.name() }, { it.value() }))
            acc
        } ?: emptyMap<String, String>()
    }
}