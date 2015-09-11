package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.http.*
import org.jetbrains.ktor.http.cookies.*
import org.jetbrains.ktor.interception.*
import java.io.*

public class NettyApplicationResponse(val response: FullHttpResponse) : ApplicationResponse {
    private val status = Interceptable1<HttpStatusCode, Unit> {
        status ->
        response.setStatus(HttpResponseStatus(status.value, status.description))
    }
    private val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    private val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteBufOutputStream(response.content())
        stream.body()
        ApplicationRequestStatus.Handled
    }

    override val headers: ResponseHeaders = object: ResponseHeaders() {
        override fun hostAppendHeader(name: String, value: String) {
            response.headers().add(name, value)
        }

        override fun getHostHeaderNames(): List<String> = response.headers().map { it.key }
        override fun getHostHeaderValues(name: String): List<String> = response.headers().getAll(name) ?: emptyList()
    }
    override val cookies = ResponseCookies(this)

    public override fun status(): HttpStatusCode? = response.status?.let { HttpStatusCode(it.code(), it.reasonPhrase()) }
    public override fun status(value: HttpStatusCode) = status.call(value)
    public override fun interceptStatus(handler: (HttpStatusCode, (HttpStatusCode) -> Unit) -> Unit) = status.intercept(handler)

    override fun send(message: Any): ApplicationRequestStatus = send.call(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        send.intercept(handler)
    }

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.call(body)

    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) {
        stream.intercept(handler)
    }
}