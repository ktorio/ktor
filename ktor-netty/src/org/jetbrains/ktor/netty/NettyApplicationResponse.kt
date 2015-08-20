package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import java.io.*

public class NettyApplicationResponse(val response: FullHttpResponse) : ApplicationResponse {
    private val header = Interceptable2<String, String, Unit> { name, value -> response.headers().set(name, value) }
    private val status = Interceptable1<Int, Unit> { code -> response.setStatus(HttpResponseStatus(code, "$code")) }
    private val send = Interceptable1<Any, ApplicationRequestStatus> { value ->
        throw UnsupportedOperationException("No known way to stream value $value")
    }

    private val stream = Interceptable1<OutputStream.() -> Unit, Unit> { body ->
        val stream = ByteBufOutputStream(response.content())
        stream.body()
        ApplicationRequestStatus.Handled
    }

    public override fun header(name: String): String? = response.headers().get(name)
    public override fun header(name: String, value: String) = header.call(name, value)
    public override fun interceptHeader(handler: (String, String, (String, String) -> Unit) -> Unit) = header.intercept(handler)

    public override fun status(): Int? = response.status?.code()
    public override fun status(value: Int) = status.call(value)
    public override fun interceptStatus(handler: (Int, (Int) -> Unit) -> Unit) = status.intercept(handler)

    override fun send(message: Any): ApplicationRequestStatus = send.call(message)
    override fun interceptSend(handler: (Any, (Any) -> ApplicationRequestStatus) -> ApplicationRequestStatus) {
        send.intercept(handler)
    }

    override fun stream(body: OutputStream.() -> Unit): Unit = stream.call(body)

    override fun interceptStream(handler: (OutputStream.() -> Unit, (OutputStream.() -> Unit) -> Unit) -> Unit) {
        stream.intercept(handler)
    }
}