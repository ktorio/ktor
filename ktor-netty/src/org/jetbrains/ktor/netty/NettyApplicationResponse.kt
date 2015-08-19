package org.jetbrains.ktor.netty

import io.netty.buffer.*
import io.netty.handler.codec.http.*
import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.interception.*
import java.io.*

public class NettyApplicationResponse(val response: FullHttpResponse) : ApplicationResponse {

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
        val stream = ByteBufOutputStream(response.content())
        stream.body()
        ApplicationRequestStatus.Handled
    }
}