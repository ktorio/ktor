package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.host.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.interception.*
import java.io.*

internal class NettyApplicationResponse(val response: FullHttpResponse) : BaseApplicationResponse() {
    override val status = Interceptable1<HttpStatusCode, Unit> {
        status ->
        response.setStatus(HttpResponseStatus(status.value, status.description))
    }

    override val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteBufOutputStream(response.content())
        stream.body()
        ApplicationCallResult.Handled
    }

    override val headers: ResponseHeaders = object : ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            response.headers().add(name, value)
        }
        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }

    public override fun status(): HttpStatusCode? = response.status?.let { HttpStatusCode(it.code(), it.reasonPhrase()) }
}